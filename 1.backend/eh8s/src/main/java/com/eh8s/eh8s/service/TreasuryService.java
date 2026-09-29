package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IWithdrawTreasuryIxBuilder;
import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.repository.interfaces.ITreasuryRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.ITreasuryService;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.WithdrawTreasuryIxBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner treasury: reads the treasury PDA ATA balance from DevNet and records verified withdrawals.
 */
@Service
public class TreasuryService implements ITreasuryService {

  static final int INFLOW_LIMIT = 20;

  private final ITreasuryRepository treasuryRepository;
  private final IWithdrawTreasuryIxBuilder ixBuilder;
  private final ISolanaRpcClient solanaRpcClient;
  private final ISolanaTxVerifier txVerifier;

  /**
   * Creates the service.
   *
   * @param treasuryRepository chain config, inflows, withdrawals
   * @param ixBuilder withdraw_treasury builder
   * @param solanaRpcClient DevNet RPC (token balance)
   * @param txVerifier DevNet transaction verifier
   */
  public TreasuryService(
      ITreasuryRepository treasuryRepository,
      IWithdrawTreasuryIxBuilder ixBuilder,
      ISolanaRpcClient solanaRpcClient,
      ISolanaTxVerifier txVerifier) {
    this.treasuryRepository = treasuryRepository;
    this.ixBuilder = ixBuilder;
    this.solanaRpcClient = solanaRpcClient;
    this.txVerifier = txVerifier;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> treasury(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    String treasuryPda = SolanaPda.treasuryPda(cfg.getProgramIdDevnet());
    String treasuryUsdc = SolanaPda.associatedTokenAddress(treasuryPda, cfg.getUsdcMint());
    long atomic = solanaRpcClient.getTokenAccountBalance(cfg.getRpcUrl(), treasuryUsdc);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    out.put("programId", cfg.getProgramIdDevnet());
    out.put("usdcMint", cfg.getUsdcMint());
    out.put("treasuryPda", treasuryPda);
    out.put("treasuryUsdc", treasuryUsdc);
    out.put("balanceAtomic", atomic);
    out.put("balanceUsdc", BigDecimal.valueOf(atomic, 6));
    out.put(
        "explorerUrl", "https://explorer.solana.com/address/" + treasuryUsdc + "?cluster=devnet");
    out.put("inflows", recentInflows(INFLOW_LIMIT));
    out.put("withdrawals", treasuryRepository.listWithdrawals());
    out.put("governanceActive", treasuryRepository.governanceActive());
    return out;
  }

  /**
   * Confirmed fee inflows (academy 85% leg, geographic subscriptions, concert fees), newest first.
   * Each row: {@code source}, {@code recordId}, {@code amountUsdc}, {@code txSignature}, {@code at}.
   */
  List<Map<String, Object>> recentInflows(int limit) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (AcademySubscription s : treasuryRepository.findPaidAcademySubscriptions(limit)) {
      rows.add(inflow("academy", s.getId(), s.getTreasuryUsdc(), s.getPayTxSignature(), s.getPaidAt()));
    }
    List<GeographicSubscription> geo = treasuryRepository.findPaidGeographicSubscriptions(limit);
    Map<Long, BigDecimal> tierPrice = new HashMap<>();
    for (GeoTier t :
        treasuryRepository.findGeoTiersByIds(
            geo.stream().map(GeographicSubscription::getGeoTierId).distinct().toList())) {
      tierPrice.put(t.getId(), t.getUsdcMonthly());
    }
    for (GeographicSubscription s : geo) {
      if (tierPrice.containsKey(s.getGeoTierId())) {
        rows.add(
            inflow(
                "geographic",
                s.getId(),
                tierPrice.get(s.getGeoTierId()),
                s.getPayTxSignature(),
                s.getPaidAt()));
      }
    }
    for (ConcertSettlement c : treasuryRepository.findSettledConcerts(limit)) {
      rows.add(
          inflow("concert_fee", c.getId(), c.getEh8sFeeUsdc(), c.getSettleTxSignature(), c.getSettledAt()));
    }
    rows.sort(
        Comparator.comparing(
                (Map<String, Object> m) -> (LocalDateTime) m.get("at"),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed());
    return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
  }

  private static Map<String, Object> inflow(
      String source, Long id, BigDecimal amount, String signature, LocalDateTime at) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("source", source);
    row.put("recordId", id);
    row.put("amountUsdc", amount);
    row.put("txSignature", signature);
    row.put("at", at);
    return row;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildWithdraw(String walletPubkey, BigDecimal amountUsdc) {
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    requireOwner(cfg, wallet);
    requireNoGovernance();
    BigDecimal amount = requiredAmount(amountUsdc);
    String treasuryUsdc =
        SolanaPda.associatedTokenAddress(
            SolanaPda.treasuryPda(cfg.getProgramIdDevnet()), cfg.getUsdcMint());
    long balance = solanaRpcClient.getTokenAccountBalance(cfg.getRpcUrl(), treasuryUsdc);
    if (WithdrawTreasuryIxBuilder.toAtomic(amount) > balance) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Treasury holds " + BigDecimal.valueOf(balance, 6).toPlainString() + " USDC");
    }
    Map<String, Object> ix =
        ixBuilder.build(
            cfg.getProgramIdDevnet(), cfg.getUsdcMint(), cfg.getRpcUrl(), wallet, amount);
    ix.put("balanceUsdc", BigDecimal.valueOf(balance, 6));
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public TreasuryWithdrawal confirmWithdraw(
      String walletPubkey, BigDecimal amountUsdc, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    requireOwner(cfg, wallet);
    BigDecimal amount = requiredAmount(amountUsdc);
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.build(programId, cfg.getUsdcMint(), cfg.getRpcUrl(), wallet, amount);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "withdraw_treasury",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(
            wallet,
            SolanaPda.configPda(programId),
            SolanaPda.treasuryPda(programId),
            (String) expected.get("treasuryUsdc"),
            (String) expected.get("ownerUsdc"),
            SolanaPda.TOKEN_PROGRAM,
            GovernanceIxBuilder.governancePda(programId)));
    TreasuryWithdrawal row = new TreasuryWithdrawal();
    row.setOwnerWalletPubkey(wallet);
    row.setAmountUsdc(BigDecimal.valueOf(WithdrawTreasuryIxBuilder.toAtomic(amount), 6));
    row.setTxSignature(txSignature);
    row.setCreatedAt(LocalDateTime.now());
    return treasuryRepository.insertWithdrawal(row);
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        treasuryRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getOwnerWalletPubkey())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config has no owner wallet");
    }
    return cfg;
  }

  private void requireNoGovernance() {
    if (treasuryRepository.governanceActive()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Governance is active: open a withdraw proposal instead");
    }
  }

  private static void requireOwner(ChainConfig cfg, String walletPubkey) {
    if (blank(walletPubkey)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    if (!cfg.getOwnerWalletPubkey().equals(walletPubkey.trim())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the platform owner wallet can use the treasury");
    }
  }

  private static BigDecimal requiredAmount(BigDecimal amount) {
    if (amount == null || WithdrawTreasuryIxBuilder.toAtomic(amount) <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountUsdc must be > 0");
    }
    return amount;
  }

  private String requireFreshSignature(String rawSignature) {
    String txSignature = requiredString(rawSignature, "txSignature");
    if (treasuryRepository.isRecorded(txSignature)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Signature already recorded");
    }
    return txSignature;
  }

  private static String requiredString(String value, String key) {
    String trimmed = value == null ? null : value.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return trimmed;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
