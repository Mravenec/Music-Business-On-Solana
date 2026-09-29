package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IBandVaultIxBuilder;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.ISettleClaimOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.BandVaultIxBuilder;
import com.eh8s.eh8s.service.solana.SettleConcertIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * BandVault activation / weight sync with DevNet confirmation, and the on-chain SPP split math.
 */
@Service
public class BandVaultService implements IBandVaultService {

  private static final BigDecimal MICRO = new BigDecimal("1000000");
  private static final int DEFAULT_FEE_BPS = 1500;

  private final IBandVaultRepository bandVaultRepository;
  private final ISettleClaimOnchainRepository settleClaimOnchainRepository;
  private final IBandVaultIxBuilder bandVaultIxBuilder;
  private final ISolanaTxVerifier txVerifier;
  private final ObjectMapper objectMapper;

  /**
   * Creates the service.
   *
   * @param bandVaultRepository band vault persistence
   * @param settleClaimOnchainRepository active chain config lookup
   * @param bandVaultIxBuilder create_band / update_spp_weights builder
   * @param txVerifier DevNet transaction verifier
   * @param objectMapper JSON for the weights column
   */
  public BandVaultService(
      IBandVaultRepository bandVaultRepository,
      ISettleClaimOnchainRepository settleClaimOnchainRepository,
      IBandVaultIxBuilder bandVaultIxBuilder,
      ISolanaTxVerifier txVerifier,
      ObjectMapper objectMapper) {
    this.bandVaultRepository = bandVaultRepository;
    this.settleClaimOnchainRepository = settleClaimOnchainRepository;
    this.bandVaultIxBuilder = bandVaultIxBuilder;
    this.txVerifier = txVerifier;
    this.objectMapper = objectMapper;
  }

  /** {@inheritDoc} */
  @Override
  public Band vault(Long bandId) {
    return requireBand(bandId);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildActivate(Long bandId, String walletPubkey) {
    Band band = requireBand(bandId);
    if (!blank(band.getBandVaultPda())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Band vault already active");
    }
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    List<Map<String, Object>> members = enrolledMembers(bandId);
    List<String> wallets = members.stream().map(m -> (String) m.get("wallet")).toList();
    Map<String, Object> ix =
        bandVaultIxBuilder.buildCreateBand(
            cfg.getProgramIdDevnet(), cfg.getRpcUrl(), cfg.getOwnerWalletPubkey(), bandId, wallets);
    ix.put("members", members);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Band confirmActivate(
      Long bandId, String walletPubkey, String rawTxSignature, String bandVaultPda) {
    Band band = requireBand(bandId);
    if (!blank(band.getBandVaultPda())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Band vault already active");
    }
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    String txSignature = requiredString(rawTxSignature, "rawTxSignature");
    String pda = requiredString(bandVaultPda, "bandVaultPda");
    String derived = bandVaultPda(cfg, bandId);
    if (!derived.equals(pda)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "bandVaultPda is not the vault of this band");
    }
    List<Map<String, Object>> members = enrolledMembers(bandId);
    List<String> wallets = members.stream().map(m -> (String) m.get("wallet")).toList();
    Map<String, Object> expected =
        bandVaultIxBuilder.buildCreateBand(
            cfg.getProgramIdDevnet(), cfg.getRpcUrl(), cfg.getOwnerWalletPubkey(), bandId, wallets);
    requireVerified(cfg, txSignature, "create_band", expected, derived);
    List<Integer> weights = BandVaultIxBuilder.equalWeights(members.size());
    return bandVaultRepository.markVaultActivated(
        bandId, pda, txSignature, toJson(withWeights(members, weights)));
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildSyncWeights(Long bandId, String walletPubkey) {
    Band band = requireActiveVault(bandId);
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    List<Map<String, Object>> rows = withWeights(syncedWeights(band), targetWeights(band));
    List<Integer> weights = rows.stream().map(r -> ((Number) r.get("bps")).intValue()).toList();
    Map<String, Object> ix =
        bandVaultIxBuilder.buildUpdateWeights(
            cfg.getProgramIdDevnet(), cfg.getRpcUrl(), cfg.getOwnerWalletPubkey(), bandId, weights);
    ix.put("members", rows);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Band confirmSyncWeights(Long bandId, String walletPubkey, String rawTxSignature) {
    Band band = requireActiveVault(bandId);
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    String txSignature = requiredString(rawTxSignature, "rawTxSignature");
    List<Map<String, Object>> rows = withWeights(syncedWeights(band), targetWeights(band));
    List<Integer> weights = rows.stream().map(r -> ((Number) r.get("bps")).intValue()).toList();
    Map<String, Object> expected =
        bandVaultIxBuilder.buildUpdateWeights(
            cfg.getProgramIdDevnet(), cfg.getRpcUrl(), cfg.getOwnerWalletPubkey(), bandId, weights);
    requireVerified(cfg, txSignature, "update_spp_weights", expected, bandVaultPda(cfg, bandId));
    return bandVaultRepository.markWeightsSynced(bandId, txSignature, toJson(rows));
  }

  /** {@inheritDoc} */
  @Override
  public List<Map<String, Object>> syncedWeights(Band band) {
    if (band == null || blank(band.getSppWeightsJson())) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          band.getSppWeightsJson(), new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "band.spp_weights_json is not valid JSON");
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<PendingClaim> claimsFromVault(Band band, BigDecimal netUsdc) {
    List<Map<String, Object>> rows = syncedWeights(band);
    List<PendingClaim> claims = new ArrayList<>();
    if (blank(band.getBandVaultPda()) || rows.isEmpty()) {
      return claims;
    }
    int feeBps =
        settleClaimOnchainRepository
            .findActiveChainConfig()
            .map(ChainConfig::getProtocolFeeBps)
            .orElse(DEFAULT_FEE_BPS);
    long netMicro = netUsdc.setScale(6, RoundingMode.HALF_UP).multiply(MICRO).longValueExact();
    long poolMicro = netMicro - SettleConcertIxBuilder.feeAtomic(netMicro, feeBps);
    List<Integer> weights = rows.stream().map(r -> ((Number) r.get("bps")).intValue()).toList();
    List<Long> shares = SettleConcertIxBuilder.splitMembers(poolMicro, weights);
    for (int i = 0; i < rows.size(); i++) {
      PendingClaim claim = new PendingClaim();
      claim.setMusicianProfileId(((Number) rows.get(i).get("musicianProfileId")).longValue());
      claim.setShareBps(weights.get(i));
      claim.setAmountUsdc(
          BigDecimal.valueOf(shares.get(i)).divide(MICRO, 2, RoundingMode.FLOOR));
      claim.setStatus("pending");
      claims.add(claim);
    }
    return claims;
  }

  /**
   * Normalizes raw SPP shares to 10000 bps in vault order (floor, dust to the last member);
   * equal split when every raw share is zero.
   *
   * @param raw raw shares in vault order
   * @return weights summing to 10000
   */
  static List<Integer> normalize(List<Integer> raw) {
    long total = raw.stream().mapToLong(Integer::longValue).sum();
    if (total <= 0) {
      return BandVaultIxBuilder.equalWeights(raw.size());
    }
    List<Integer> out = new ArrayList<>();
    int allocated = 0;
    for (int i = 0; i < raw.size(); i++) {
      if (i == raw.size() - 1) {
        out.add(10_000 - allocated);
      } else {
        int w = (int) (raw.get(i) * 10_000L / total);
        allocated += w;
        out.add(w);
      }
    }
    return out;
  }

  private List<Integer> targetWeights(Band band) {
    List<Map<String, Object>> rows = syncedWeights(band);
    Map<Long, Integer> byProfile = new LinkedHashMap<>();
    for (SppMemberScore s : bandVaultRepository.findLatestClosedScores(band.getId())) {
      byProfile.put(s.getMusicianProfileId(), s.getShareBps() == null ? 0 : s.getShareBps());
    }
    List<Integer> raw =
        rows.stream()
            .map(r -> byProfile.getOrDefault(((Number) r.get("musicianProfileId")).longValue(), 0))
            .toList();
    return normalize(raw);
  }

  /** Musician profile id to linked account wallet (null when the account has no wallet). */
  private Map<Long, String> walletsByProfile(List<MusicianProfile> profiles) {
    Map<Long, String> walletByAccount = new HashMap<>();
    for (Account a :
        bandVaultRepository.findAccountsByIds(
            profiles.stream().map(MusicianProfile::getAccountId).distinct().toList())) {
      walletByAccount.put(a.getId(), a.getWalletPubkey());
    }
    Map<Long, String> out = new HashMap<>();
    for (MusicianProfile p : profiles) {
      out.put(p.getId(), walletByAccount.get(p.getAccountId()));
    }
    return out;
  }

  private List<Map<String, Object>> enrolledMembers(Long bandId) {
    List<MusicianProfile> profiles = bandVaultRepository.findMemberProfiles(bandId);
    if (profiles.isEmpty() || profiles.size() > BandVaultIxBuilder.MAX_MEMBERS) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A band vault needs 1 to 8 members");
    }
    Map<Long, String> wallets = walletsByProfile(profiles);
    List<Map<String, Object>> out = new ArrayList<>();
    List<Long> missing = new ArrayList<>();
    for (MusicianProfile p : profiles) {
      String wallet = wallets.get(p.getId());
      if (blank(wallet)) {
        missing.add(p.getId());
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("musicianProfileId", p.getId());
      row.put("wallet", wallet);
      out.add(row);
    }
    if (!missing.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Every band member needs a linked wallet first; musician profiles without a wallet "
              + missing);
    }
    return out;
  }

  private static List<Map<String, Object>> withWeights(
      List<Map<String, Object>> members, List<Integer> weights) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 0; i < members.size(); i++) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("musicianProfileId", members.get(i).get("musicianProfileId"));
      row.put("wallet", members.get(i).get("wallet"));
      row.put("bps", weights.get(i));
      out.add(row);
    }
    return out;
  }

  private Band requireActiveVault(Long bandId) {
    Band band = requireBand(bandId);
    if (blank(band.getBandVaultPda())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Activate the band vault on-chain first");
    }
    return band;
  }

  private Band requireBand(Long bandId) {
    return bandVaultRepository
        .findBand(bandId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown band"));
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        settleClaimOnchainRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getRpcUrl()) || blank(cfg.getProgramIdDevnet()) || blank(cfg.getOwnerWalletPubkey())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config missing rpc/program/owner");
    }
    return cfg;
  }

  private static void requireOwner(ChainConfig cfg, String walletPubkey) {
    String wallet = requiredString(walletPubkey, "walletPubkey");
    if (!wallet.equals(cfg.getOwnerWalletPubkey())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner wallet signs band vault instructions");
    }
  }

  private void requireVerified(
      ChainConfig cfg,
      String txSignature,
      String instruction,
      Map<String, Object> expected,
      String bandVaultPda) {
    String programId = cfg.getProgramIdDevnet();
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        instruction,
        (String) expected.get("dataHex"),
        cfg.getOwnerWalletPubkey(),
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), bandVaultPda));
  }

  private static String bandVaultPda(ChainConfig cfg, Long bandId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("band"), SolanaPda.u64le(bandId)), cfg.getProgramIdDevnet());
  }

  private String toJson(List<Map<String, Object>> rows) {
    try {
      return objectMapper.writeValueAsString(rows);
    } catch (JsonProcessingException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "weights JSON failed");
    }
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
