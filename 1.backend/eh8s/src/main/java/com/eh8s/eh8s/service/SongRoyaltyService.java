package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.ISongRoyaltyIxBuilder;
import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.ISongRoyaltyRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.ISongRoyaltyService;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SongRoyaltyIxBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-song royalty pools, per-song deposits and sync licensing 80/20 on DevNet program v0.10.0.
 * Builds instructions for the wallet, then verifies each signature over RPC before persisting.
 */
@Service
public class SongRoyaltyService implements ISongRoyaltyService {

  private static final BigDecimal MICRO = BigDecimal.valueOf(1_000_000L);

  private final ISongRoyaltyRepository songRoyaltyRepository;
  private final IBandVaultRepository bandVaultRepository;
  private final IAgentAuthorityRepository agentRepository;
  private final ISongRoyaltyIxBuilder ixBuilder;
  private final ISolanaTxVerifier txVerifier;
  private final ISolanaRpcClient rpc;
  private final ObjectMapper objectMapper;

  /**
   * Creates the service.
   *
   * @param songRoyaltyRepository pool / deposit / sync deal persistence
   * @param bandVaultRepository band members and profile wallets
   * @param agentRepository live agent authorizations (WAVE bit)
   * @param ixBuilder per-song instruction builder
   * @param txVerifier DevNet transaction verifier
   * @param rpc DevNet RPC (pool and profile account reads)
   * @param objectMapper JSON for the split snapshot
   */
  public SongRoyaltyService(
      ISongRoyaltyRepository songRoyaltyRepository,
      IBandVaultRepository bandVaultRepository,
      IAgentAuthorityRepository agentRepository,
      ISongRoyaltyIxBuilder ixBuilder,
      ISolanaTxVerifier txVerifier,
      ISolanaRpcClient rpc,
      ObjectMapper objectMapper) {
    this.songRoyaltyRepository = songRoyaltyRepository;
    this.bandVaultRepository = bandVaultRepository;
    this.agentRepository = agentRepository;
    this.ixBuilder = ixBuilder;
    this.txVerifier = txVerifier;
    this.rpc = rpc;
    this.objectMapper = objectMapper;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> poolStatus(Long trackId) {
    Track track = requireTrack(trackId);
    ChainConfig cfg = requireChainConfig();
    String pool = SongRoyaltyIxBuilder.poolPda(trackId, cfg.getProgramIdDevnet());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("trackId", trackId);
    out.put("title", track.getTitle());
    out.put("royaltyPoolPda", pool);
    out.put("active", track.getPoolActivatedAt() != null);
    out.put("poolActivatedAt", track.getPoolActivatedAt());
    out.put("poolTxSignature", track.getPoolTxSignature());
    out.put("splits", track.getPoolActivatedAt() == null ? null : snapshot(track));
    if (track.getPoolActivatedAt() == null) {
      try {
        out.put("proposedSplits", toRows(resolveSplits(track, null)));
      } catch (ResponseStatusException ex) {
        out.put("proposedSplits", List.of());
        out.put("proposedError", ex.getReason());
      }
    }
    try {
      Map<String, Object> onchain = SongRoyaltyIxBuilder.decodePool(rpc.getAccountData(cfg.getRpcUrl(), pool));
      out.put("onchainExists", onchain != null);
      if (onchain != null) {
        out.put("onchainTotalUsdc", usdc((Long) onchain.get("totalAtomic")));
        out.put("onchainSyncTotalUsdc", usdc((Long) onchain.get("syncTotalAtomic")));
        out.put("onchainMembers", onchain.get("members"));
        out.put("onchainSplitsBps", onchain.get("splitsBps"));
      }
    } catch (ResponseStatusException ex) {
      out.put("onchainError", ex.getReason());
    }
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildActivatePool(
      Long trackId, String walletPubkey, List<RoyaltySplit> splits) {
    Track track = requireTrack(trackId);
    requireNotActivated(track);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String agent = waveAccount(cfg, wallet);
    List<Member> members = resolveSplits(track, splits);
    String programId = cfg.getProgramIdDevnet();
    String pool = SongRoyaltyIxBuilder.poolPda(trackId, programId);
    if (rpc.getAccountData(cfg.getRpcUrl(), pool) != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "RoyaltyPool already exists on DevNet for this song; confirm it with its signature");
    }
    for (Member m : members) {
      String profile = SolanaPda.walletPda("musician", m.wallet(), programId);
      if (rpc.getAccountData(cfg.getRpcUrl(), profile) == null) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Member profile " + m.musicianProfileId() + " has no on-chain MusicianProfile yet");
      }
    }
    Map<String, Object> ix = createPoolIx(cfg, wallet, trackId, members, agent);
    ix.put("members", toRows(members));
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public Track confirmActivatePool(
      Long trackId, String walletPubkey, String rawTxSignature, List<RoyaltySplit> splits) {
    Track track = requireTrack(trackId);
    requireNotActivated(track);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String agent = waveAccount(cfg, wallet);
    List<Member> members = resolveSplits(track, splits);
    String programId = cfg.getProgramIdDevnet();
    String pool = SongRoyaltyIxBuilder.poolPda(trackId, programId);
    Map<String, Object> expected = createPoolIx(cfg, wallet, trackId, members, agent);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "create_royalty_pool",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), pool));
    return songRoyaltyRepository.markPoolActivated(trackId, pool, toJson(members), txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildDeposit(Long depositId, String walletPubkey) {
    RoyaltyDeposit deposit = requireOpenDeposit(depositId);
    Track track = requireActivePool(deposit.getTrackId());
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String agent = waveAccount(cfg, wallet);
    Map<String, Object> ix = depositIx(cfg, wallet, track, deposit, agent);
    ix.put("depositId", depositId);
    ix.put("amountUsdc", deposit.getAmountUsdc());
    ix.put("members", snapshot(track));
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public RoyaltyDeposit confirmDeposit(Long depositId, String walletPubkey, String rawTxSignature) {
    RoyaltyDeposit deposit = requireOpenDeposit(depositId);
    Track track = requireActivePool(deposit.getTrackId());
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String agent = waveAccount(cfg, wallet);
    String programId = cfg.getProgramIdDevnet();
    String pool = SongRoyaltyIxBuilder.poolPda(track.getId(), programId);
    Map<String, Object> expected = depositIx(cfg, wallet, track, deposit, agent);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "deposit_royalties",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), pool));
    return songRoyaltyRepository.markDepositConfirmed(depositId, wallet, txSignature, pool);
  }

  /** {@inheritDoc} */
  @Override
  public List<SyncLicenseDeal> deals(Long trackId) {
    return songRoyaltyRepository.findDeals(trackId);
  }

  /** {@inheritDoc} */
  @Override
  public SyncLicenseDeal createDeal(SyncLicenseDeal deal) {
    if (deal == null || deal.getTrackId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trackId is required");
    }
    requireTrack(deal.getTrackId());
    String name = deal.getLicenseeName() == null ? "" : deal.getLicenseeName().trim();
    if (name.isEmpty() || name.length() > 120) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "licenseeName is required (max 120 characters)");
    }
    String use = deal.getUseDescription() == null ? null : deal.getUseDescription().trim();
    if (use != null && use.length() > 255) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "useDescription is max 255 characters");
    }
    BigDecimal amount = deal.getAmountUsdc();
    if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "amountUsdc must be > 0 with at most 2 decimals");
    }
    BigDecimal eh8s =
        amount
            .multiply(BigDecimal.valueOf(SongRoyaltyIxBuilder.SYNC_FEE_BPS))
            .divide(BigDecimal.valueOf(10_000L), 2, RoundingMode.DOWN);
    SyncLicenseDeal row = new SyncLicenseDeal();
    row.setTrackId(deal.getTrackId());
    row.setLicenseeName(name);
    row.setUseDescription(use == null || use.isEmpty() ? null : use);
    row.setAmountUsdc(amount.setScale(2, RoundingMode.UNNECESSARY));
    row.setEh8sUsdc(eh8s);
    row.setArtistUsdc(amount.subtract(eh8s).setScale(2, RoundingMode.UNNECESSARY));
    row.setStatus("proposed");
    return songRoyaltyRepository.insertDeal(row);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildPaySync(Long dealId, String walletPubkey) {
    SyncLicenseDeal deal = requireOpenDeal(dealId);
    Track track = requireActivePool(deal.getTrackId());
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    Map<String, Object> ix = syncIx(cfg, wallet, track, deal);
    ix.put("amountUsdc", deal.getAmountUsdc());
    ix.put("artistUsdc", deal.getArtistUsdc());
    ix.put("eh8sUsdc", deal.getEh8sUsdc());
    ix.put("members", snapshot(track));
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public SyncLicenseDeal confirmPaySync(Long dealId, String walletPubkey, String rawTxSignature) {
    SyncLicenseDeal deal = requireOpenDeal(dealId);
    Track track = requireActivePool(deal.getTrackId());
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    String pool = SongRoyaltyIxBuilder.poolPda(track.getId(), programId);
    String license = SongRoyaltyIxBuilder.syncPda(track.getId(), dealId, programId);
    Map<String, Object> expected = syncIx(cfg, wallet, track, deal);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "pay_sync_license",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), pool, license));
    return songRoyaltyRepository.markDealPaid(dealId, wallet, license, txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public List<Map<String, Object>> songCredits(String walletPubkey) {
    if (walletPubkey == null || walletPubkey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    String wallet = walletPubkey.trim();
    Map<Long, Track> tracks = new LinkedHashMap<>();
    Map<Long, Integer> bpsByTrack = new LinkedHashMap<>();
    for (Track t : songRoyaltyRepository.findActivatedTracks()) {
      for (Map<String, Object> m : snapshot(t)) {
        if (wallet.equals(m.get("wallet"))) {
          tracks.put(t.getId(), t);
          bpsByTrack.put(t.getId(), ((Number) m.get("bps")).intValue());
        }
      }
    }
    List<Long> ids = new ArrayList<>(tracks.keySet());
    List<Map<String, Object>> out = new ArrayList<>();
    for (RoyaltyDeposit d : songRoyaltyRepository.findConfirmedDeposits(ids)) {
      long gross = SongRoyaltyIxBuilder.atomic(d.getAmountUsdc());
      out.add(
          credit(
              tracks.get(d.getTrackId()),
              "deposit",
              d.getId(),
              d.getAmountUsdc(),
              bpsByTrack.get(d.getTrackId()),
              gross,
              d.getDepositTxSignature(),
              d.getDepositedAt()));
    }
    for (SyncLicenseDeal s : songRoyaltyRepository.findPaidDeals(ids)) {
      long gross = SongRoyaltyIxBuilder.atomic(s.getAmountUsdc());
      long artist = gross - gross * SongRoyaltyIxBuilder.SYNC_FEE_BPS / 10_000L;
      out.add(
          credit(
              tracks.get(s.getTrackId()),
              "sync",
              s.getId(),
              s.getAmountUsdc(),
              bpsByTrack.get(s.getTrackId()),
              artist,
              s.getPayTxSignature(),
              s.getPaidAt()));
    }
    out.sort(
        Comparator.comparing(
                (Map<String, Object> r) -> (LocalDateTime) r.get("at"),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed());
    return out;
  }

  private static Map<String, Object> credit(
      Track track,
      String source,
      Long sourceId,
      BigDecimal grossUsdc,
      int bps,
      long creditableAtomic,
      String txSignature,
      LocalDateTime at) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("trackId", track.getId());
    row.put("trackTitle", track.getTitle());
    row.put("source", source);
    row.put("sourceId", sourceId);
    row.put("grossUsdc", grossUsdc);
    row.put("shareBps", bps);
    row.put("creditUsdc", usdc(creditableAtomic * bps / 10_000L));
    row.put("txSignature", txSignature);
    row.put("at", at);
    return row;
  }

  private Map<String, Object> createPoolIx(
      ChainConfig cfg, String wallet, long trackId, List<Member> members, String agent) {
    return ixBuilder.buildCreatePool(
        cfg.getProgramIdDevnet(),
        cfg.getRpcUrl(),
        wallet,
        trackId,
        members.stream().map(Member::wallet).toList(),
        members.stream().map(Member::bps).toList(),
        agent);
  }

  private Map<String, Object> depositIx(
      ChainConfig cfg, String wallet, Track track, RoyaltyDeposit deposit, String agent) {
    return ixBuilder.buildDeposit(
        cfg.getProgramIdDevnet(),
        cfg.getRpcUrl(),
        cfg.getUsdcMint(),
        wallet,
        track.getId(),
        SongRoyaltyIxBuilder.atomic(deposit.getAmountUsdc()),
        agent,
        memberWallets(track));
  }

  private Map<String, Object> syncIx(
      ChainConfig cfg, String wallet, Track track, SyncLicenseDeal deal) {
    return ixBuilder.buildSyncPay(
        cfg.getProgramIdDevnet(),
        cfg.getRpcUrl(),
        cfg.getUsdcMint(),
        wallet,
        track.getId(),
        deal.getId(),
        SongRoyaltyIxBuilder.atomic(deal.getAmountUsdc()),
        memberWallets(track));
  }

  /** Musician profile id to linked account wallet (null when the account has no wallet). */
  private Map<Long, String> walletsByProfile(List<Long> profileIds) {
    List<MusicianProfile> profiles = bandVaultRepository.findProfilesByIds(profileIds);
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

  /**
   * Explicit {@code splits} (must sum 10000), else royalty_split rows with a musician
   * profile (normalized), else the band members split equally.
   */
  private List<Member> resolveSplits(Track track, List<RoyaltySplit> splits) {
    Map<Long, Integer> raw = new LinkedHashMap<>();
    if (splits != null) {
      for (RoyaltySplit item : splits) {
        if (item == null
            || item.getMusicianProfileId() == null
            || item.getShareBps() == null
            || item.getShareBps() <= 0) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "splits must be [{musicianProfileId, bps > 0}]");
        }
        raw.merge(item.getMusicianProfileId(), item.getShareBps(), Integer::sum);
      }
      if (raw.values().stream().mapToInt(Integer::intValue).sum() != 10_000) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "splits must sum to 10000 bps");
      }
    } else {
      for (RoyaltySplit s : songRoyaltyRepository.findSplits(track.getId())) {
        if (s.getMusicianProfileId() != null && s.getShareBps() != null && s.getShareBps() > 0) {
          raw.merge(s.getMusicianProfileId(), s.getShareBps(), Integer::sum);
        }
      }
      if (raw.isEmpty() && track.getBandId() != null) {
        for (MusicianProfile p : bandVaultRepository.findMemberProfiles(track.getBandId())) {
          raw.put(p.getId(), 1);
        }
      }
      raw = normalize(raw);
    }
    if (raw.isEmpty() || raw.size() > SongRoyaltyIxBuilder.MAX_MEMBERS) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A song pool needs 1 to 8 musician members (splits or band)");
    }
    Map<Long, String> wallets = walletsByProfile(new ArrayList<>(raw.keySet()));
    List<Long> missing = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    List<Member> out = new ArrayList<>();
    for (Map.Entry<Long, Integer> e : raw.entrySet()) {
      String wallet = wallets.get(e.getKey());
      if (wallet == null || wallet.isBlank()) {
        missing.add(e.getKey());
        continue;
      }
      if (!seen.add(wallet)) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Two split members share wallet " + wallet);
      }
      if (e.getValue() <= 0) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Split for profile " + e.getKey() + " rounds to 0 bps");
      }
      out.add(new Member(e.getKey(), wallet, e.getValue()));
    }
    if (!missing.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Musician profiles without a wallet: " + missing);
    }
    return out;
  }

  private static Map<Long, Integer> normalize(Map<Long, Integer> raw) {
    long total = raw.values().stream().mapToLong(Integer::longValue).sum();
    Map<Long, Integer> out = new LinkedHashMap<>();
    if (total <= 0) {
      return out;
    }
    int allocated = 0;
    int i = 0;
    for (Map.Entry<Long, Integer> e : raw.entrySet()) {
      int w =
          ++i == raw.size() ? 10_000 - allocated : (int) (e.getValue() * 10_000L / total);
      allocated += w;
      out.put(e.getKey(), w);
    }
    return out;
  }

  private List<Map<String, Object>> snapshot(Track track) {
    if (track.getPoolSplitsJson() == null || track.getPoolSplitsJson().isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          track.getPoolSplitsJson(), new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "track.pool_splits_json is unreadable");
    }
  }

  private List<String> memberWallets(Track track) {
    return snapshot(track).stream().map(m -> String.valueOf(m.get("wallet"))).toList();
  }

  private String toJson(List<Member> members) {
    try {
      return objectMapper.writeValueAsString(toRows(members));
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Split JSON failed");
    }
  }

  private static List<Map<String, Object>> toRows(List<Member> members) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Member m : members) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("musicianProfileId", m.musicianProfileId());
      row.put("wallet", m.wallet());
      row.put("bps", m.bps());
      rows.add(row);
    }
    return rows;
  }

  /** Owner signs with the program id as the Option placeholder; else a WAVE agent PDA or 403. */
  private String waveAccount(ChainConfig cfg, String wallet) {
    String programId = cfg.getProgramIdDevnet();
    if (wallet.equals(cfg.getOwnerWalletPubkey())) {
      return programId;
    }
    boolean hasBit =
        agentRepository
            .findLatestAuthorization(wallet)
            .map(
                a ->
                    a.getPermissions() != null
                        && (a.getPermissions() & AgentAuthorityIxBuilder.PERM_WAVE) != 0)
            .orElse(false);
    if (!hasBit) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner or a WAVE agent can do this");
    }
    return SolanaPda.walletPda("agent", wallet, programId);
  }

  private String requireFreshSignature(String rawSignature) {
    String txSignature = requiredString(rawSignature, "txSignature");
    if (songRoyaltyRepository.isSignatureRecorded(txSignature)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "txSignature already recorded");
    }
    return txSignature;
  }

  private Track requireTrack(Long trackId) {
    return songRoyaltyRepository
        .findTrack(trackId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown track"));
  }

  private static void requireNotActivated(Track track) {
    if (track.getPoolActivatedAt() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Song royalty pool is already activated");
    }
  }

  private Track requireActivePool(Long trackId) {
    Track track = requireTrack(trackId);
    if (track.getPoolActivatedAt() == null || snapshot(track).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Activate the song royalty pool first");
    }
    return track;
  }

  private RoyaltyDeposit requireOpenDeposit(Long depositId) {
    RoyaltyDeposit deposit =
        songRoyaltyRepository
            .findDeposit(depositId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown deposit"));
    if ("confirmed".equals(deposit.getOnChainStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Deposit is already confirmed");
    }
    if (deposit.getAmountUsdc() == null || deposit.getAmountUsdc().signum() <= 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Deposit has no stored amount");
    }
    return deposit;
  }

  private SyncLicenseDeal requireOpenDeal(Long dealId) {
    SyncLicenseDeal deal =
        songRoyaltyRepository
            .findDeal(dealId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown sync deal"));
    if ("paid".equals(deal.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Sync deal is already paid");
    }
    return deal;
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        songRoyaltyRepository
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

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static BigDecimal usdc(long atomic) {
    return BigDecimal.valueOf(atomic).divide(MICRO, 6, RoundingMode.UNNECESSARY);
  }

  private record Member(Long musicianProfileId, String wallet, int bps) {}
}
