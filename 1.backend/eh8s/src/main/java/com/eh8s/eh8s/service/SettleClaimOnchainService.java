package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.ISettleConcertIxBuilder;
import com.eh8s.eh8s.service.interfaces.IClaimRoyaltiesIxBuilder;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.ISettleClaimOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import com.eh8s.eh8s.service.interfaces.ISettleClaimOnchainService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.ClaimRoyaltiesIxBuilder;
import com.eh8s.eh8s.service.solana.SettleConcertIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * DevNet settle_concert / claim_royalties build + RPC confirm. Never marks claimed without a
 * confirmed signature that transferred USDC.
 */
@Service
public class SettleClaimOnchainService implements ISettleClaimOnchainService {

  private final ISettleClaimOnchainRepository settleClaimOnchainRepository;
  private final IBandVaultRepository bandVaultRepository;
  private final IBandVaultService bandVaultService;
  private final ISettleConcertIxBuilder settleConcertIxBuilder;
  private final IClaimRoyaltiesIxBuilder claimRoyaltiesIxBuilder;
  private final ISolanaTxVerifier txVerifier;

  /**
   * Creates the service.
   *
   * @param settleClaimOnchainRepository settlement/claim persistence
   * @param bandVaultRepository band of a concert (vault PDA)
   * @param bandVaultService synced BandVault weights
   * @param settleConcertIxBuilder settle_concert instruction builder
   * @param claimRoyaltiesIxBuilder claim_royalties instruction builder
   * @param txVerifier DevNet transaction verifier
   */
  public SettleClaimOnchainService(
      ISettleClaimOnchainRepository settleClaimOnchainRepository,
      IBandVaultRepository bandVaultRepository,
      IBandVaultService bandVaultService,
      ISettleConcertIxBuilder settleConcertIxBuilder,
      IClaimRoyaltiesIxBuilder claimRoyaltiesIxBuilder,
      ISolanaTxVerifier txVerifier) {
    this.settleClaimOnchainRepository = settleClaimOnchainRepository;
    this.bandVaultRepository = bandVaultRepository;
    this.bandVaultService = bandVaultService;
    this.settleConcertIxBuilder = settleConcertIxBuilder;
    this.claimRoyaltiesIxBuilder = claimRoyaltiesIxBuilder;
    this.txVerifier = txVerifier;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildSettleConcert(Long settlementId, String walletPubkey) {
    ConcertSettlement settlement = requireSettlement(settlementId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    Map<String, Object> ix = settleIx(settlement, cfg, wallet);
    ix.put("settlementId", settlementId);
    return ix;
  }

  /**
   * settle_concert payload for a settlement and venue wallet, from MariaDB state only (same
   * result at build and at confirm).
   */
  private Map<String, Object> settleIx(
      ConcertSettlement settlement, ChainConfig cfg, String wallet) {
    BigDecimal gross = nullToZero(settlement.getGrossUsdc());
    BigDecimal expenses = nullToZero(settlement.getExpensesUsdc());
    if (gross.subtract(expenses).compareTo(BigDecimal.ZERO) <= 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Settlement net (gross − expenses) must be > 0");
    }
    if (settlement.getConcertId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Settlement has no concert id");
    }
    int feeBps = cfg.getProtocolFeeBps() == null ? 1500 : cfg.getProtocolFeeBps();
    String owner = cfg.getOwnerWalletPubkey();
    if (blank(owner)) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config missing ownerWalletPubkey");
    }
    Band band =
        bandVaultRepository
            .findBandByConcert(settlement.getConcertId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concert has no band"));
    List<Map<String, Object>> weights = bandVaultService.syncedWeights(band);
    if (blank(band.getBandVaultPda()) || weights.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Activate the band vault on-chain before settling this concert");
    }
    List<String> memberWallets = weights.stream().map(w -> (String) w.get("wallet")).toList();
    List<Integer> weightsBps =
        weights.stream().map(w -> ((Number) w.get("bps")).intValue()).toList();
    Map<String, Object> ix =
        settleConcertIxBuilder.build(
            cfg.getProgramIdDevnet(),
            cfg.getRpcUrl(),
            cfg.getUsdcMint(),
            wallet,
            owner,
            band.getId(),
            memberWallets,
            weightsBps,
            settlement.getConcertId(),
            gross,
            expenses,
            feeBps);
    ix.put("concertId", settlement.getConcertId());
    ix.put("ownerWalletPubkey", owner);
    ix.put("bandVaultPda", band.getBandVaultPda());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public ConcertSettlement confirmSettleConcert(
      Long settlementId, String walletPubkey, String rawTxSignature, String concertSettlementPda) {
    ConcertSettlement settlement = requireSettlement(settlementId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String pda = requiredString(concertSettlementPda, "concertSettlementPda");
    Map<String, Object> expected = settleIx(settlement, cfg, wallet);
    String programId = cfg.getProgramIdDevnet();
    long bandId = ((Number) expected.get("bandId")).longValue();
    String concertPda =
        SolanaPda.findProgramAddress(
            List.of(
                SolanaPda.seed("concert"),
                SolanaPda.decode(wallet),
                SolanaPda.u64le(settlement.getConcertId())),
            programId);
    if (!concertPda.equals(pda)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "concertSettlementPda does not match this venue and concert");
    }
    @SuppressWarnings("unchecked")
    List<String> members = (List<String>) expected.get("memberWallets");
    String vaultPda =
        SolanaPda.findProgramAddress(List.of(SolanaPda.seed("vault")), programId);
    List<String> accounts = new ArrayList<>(Collections.nCopies(9 + members.size(), null));
    accounts.set(0, wallet);
    accounts.set(1, SolanaPda.configPda(programId));
    accounts.set(
        2,
        SolanaPda.findProgramAddress(
            List.of(SolanaPda.seed("band"), SolanaPda.u64le(bandId)), programId));
    accounts.set(3, concertPda);
    accounts.set(4, SolanaPda.associatedTokenAddress(wallet, cfg.getUsdcMint()));
    accounts.set(5, SolanaPda.associatedTokenAddress(SolanaPda.treasuryPda(programId), cfg.getUsdcMint()));
    accounts.set(6, SolanaPda.associatedTokenAddress(vaultPda, cfg.getUsdcMint()));
    accounts.set(7, SolanaPda.TOKEN_PROGRAM);
    accounts.set(8, SolanaPda.SYSTEM_PROGRAM);
    for (int i = 0; i < members.size(); i++) {
      accounts.set(9 + i, SolanaPda.walletPda("musician", members.get(i), programId));
    }
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "settle_concert",
        (String) expected.get("dataHex"),
        wallet,
        accounts);
    return settleClaimOnchainRepository.markSettlementConfirmed(
        settlementId, wallet, txSignature, pda);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildClaimRoyalties(
      Long claimId,
      String walletPubkey,
      BigDecimal amountUsdc,
      String vaultUsdcAta,
      String musicianUsdcAta) {
    PendingClaim claim = requireClaim(claimId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    BigDecimal stored = nullToZero(claim.getAmountUsdc());
    BigDecimal amount =
        stored.compareTo(BigDecimal.ZERO) > 0 ? stored : Optional.ofNullable(amountUsdc).orElse(stored);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountUsdc must be > 0");
    }
    String vaultUsdc = optionalString(vaultUsdcAta);
    String musicianUsdc = optionalString(musicianUsdcAta);
    Map<String, Object> ix =
        claimRoyaltiesIxBuilder.build(
            cfg.getProgramIdDevnet(),
            cfg.getUsdcMint(),
            cfg.getRpcUrl(),
            wallet,
            amount,
            vaultUsdc,
            musicianUsdc);
    ix.put("claimId", claimId);
    ix.put("concertSettlementId", claim.getConcertSettlementId());
    ix.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public PendingClaim confirmClaimRoyalties(
      Long claimId, String walletPubkey, String rawTxSignature, String claimPda) {
    PendingClaim claim = requireClaim(claimId);
    ConcertSettlement settlement = requireSettlement(claim.getConcertSettlementId());
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String pda = requiredString(claimPda, "claimPda");
    if (!"confirmed".equalsIgnoreCase(settlement.getOnChainStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Settlement not confirmed on DevNet; confirm settle_concert before claim_royalties");
    }

    String programId = cfg.getProgramIdDevnet();
    String profilePda = SolanaPda.walletPda("musician", wallet, programId);
    if (!profilePda.equals(pda)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "claimPda is not the musician profile of this wallet");
    }
    BigDecimal amount = nullToZero(claim.getAmountUsdc());
    Map<String, Object> expected =
        claimRoyaltiesIxBuilder.build(
            programId, cfg.getUsdcMint(), cfg.getRpcUrl(), wallet, amount, null, null);
    String vaultPda = SolanaPda.findProgramAddress(List.of(SolanaPda.seed("vault")), programId);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "claim_royalties",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(
            wallet,
            SolanaPda.configPda(programId),
            profilePda,
            vaultPda,
            SolanaPda.associatedTokenAddress(vaultPda, cfg.getUsdcMint()),
            SolanaPda.associatedTokenAddress(wallet, cfg.getUsdcMint()),
            SolanaPda.TOKEN_PROGRAM));
    return settleClaimOnchainRepository.markClaimConfirmed(claimId, wallet, txSignature, pda);
  }

  private String requireFreshSignature(String rawSignature) {
    String txSignature = requiredString(rawSignature, "txSignature");
    if (settleClaimOnchainRepository.isRecorded(txSignature)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Signature already recorded");
    }
    return txSignature;
  }

  private ConcertSettlement requireSettlement(Long settlementId) {
    return settleClaimOnchainRepository
        .findSettlement(settlementId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown settlement"));
  }

  private PendingClaim requireClaim(Long claimId) {
    return settleClaimOnchainRepository
        .findClaim(claimId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown claim"));
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        settleClaimOnchainRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getRpcUrl()) || blank(cfg.getProgramIdDevnet()) || blank(cfg.getUsdcMint())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config missing rpc/program/usdc mint");
    }
    return cfg;
  }

  private static String requiredString(String value, String key) {
    String trimmed = value == null ? null : value.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return trimmed;
  }

  private static String optionalString(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }


  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static BigDecimal nullToZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
