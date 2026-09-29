package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.ISongRoyaltyRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.SettleClaimIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SongRoyaltyIxBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Unit tests for per-song pools, per-song deposits, sync deals 80/20 and per-song credits. */
class SongRoyaltyServiceTest {

  static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String WAVE = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";
  static final String A = "CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o";
  static final String B = "U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq";
  static final String C = "9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z";
  static final String MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";

  private final Track track = new Track();
  private final List<RoyaltySplit> splits = new ArrayList<>();
  private final List<MusicianProfile> bandProfiles = new ArrayList<>();
  private final Map<Long, String> wallets = new HashMap<>();
  private final Set<String> missingAccounts = new HashSet<>();
  private final Set<String> recorded = new HashSet<>();
  private final List<AgentAuthority> authorizations = new ArrayList<>();
  private final Map<String, Object> verified = new HashMap<>();
  private final Map<String, Object> stored = new HashMap<>();
  private final RoyaltyDeposit deposit = new RoyaltyDeposit();
  private final SyncLicenseDeal deal = new SyncLicenseDeal();

  @BeforeEach
  void seed() {
    track.setId(7L);
    track.setBandId(2L);
    track.setTitle("Neon Nights");
    wallets.put(11L, A);
    wallets.put(12L, B);
    wallets.put(13L, C);
    deposit.setId(3L);
    deposit.setTrackId(7L);
    deposit.setAmountUsdc(new BigDecimal("50.00"));
    deal.setId(9L);
    deal.setTrackId(7L);
    deal.setAmountUsdc(new BigDecimal("100.00"));
    deal.setArtistUsdc(new BigDecimal("80.00"));
    deal.setEh8sUsdc(new BigDecimal("20.00"));
    deal.setStatus("proposed");
  }

  @Test
  void builderEncodesCreatePoolDepositAndSync() {
    SongRoyaltyIxBuilder b = new SongRoyaltyIxBuilder();
    Map<String, Object> create =
        b.buildCreatePool(PROGRAM, "rpc", OWNER, 7L, List.of(A, B), List.of(7_500, 2_500), PROGRAM);
    ByteBuffer args = ByteBuffer.allocate(8 + 4 + 64 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
    args.putLong(7L).putInt(2).put(SolanaPda.decode(A)).put(SolanaPda.decode(B));
    args.putInt(2).putShort((short) 7_500).putShort((short) 2_500);
    assertEquals(disc("create_royalty_pool") + hex(args.array()), create.get("dataHex"));
    assertEquals(
        SolanaPda.findProgramAddress(List.of(SolanaPda.seed("royalty"), SolanaPda.u64le(7L)), PROGRAM),
        create.get("royaltyPoolPda"));
    assertEquals(5, ((List<?>) create.get("accounts")).size());

    Map<String, Object> dep = b.buildDeposit(PROGRAM, "rpc", MINT, OWNER, 7L, 50_000_000L, PROGRAM, List.of(A, B));
    ByteBuffer depArgs = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(7L).putLong(50_000_000L);
    assertEquals(disc("deposit_royalties") + hex(depArgs.array()), dep.get("dataHex"));
    List<?> depAccounts = (List<?>) dep.get("accounts");
    assertEquals(9, depAccounts.size());
    assertEquals(SolanaPda.walletPda("musician", B, PROGRAM), ((Map<?, ?>) depAccounts.get(8)).get("pubkey"));

    Map<String, Object> sync = b.buildSyncPay(PROGRAM, "rpc", MINT, C, 7L, 9L, 100_000_000L, List.of(A));
    assertEquals(SongRoyaltyIxBuilder.syncPda(7L, 9L, PROGRAM), sync.get("syncLicensePda"));
    assertEquals(20_000_000L, sync.get("eh8sFeeAtomic"));
    assertEquals(80_000_000L, sync.get("artistAtomic"));
    assertEquals(10, ((List<?>) sync.get("accounts")).size());
  }

  @Test
  void decodePoolReadsMembersSplitsAndTotals() {
    Map<String, Object> pool = SongRoyaltyIxBuilder.decodePool(poolAccount(7L, 12_000_000L, 4_000_000L));
    assertEquals(7L, pool.get("trackId"));
    assertEquals(List.of(A, B), pool.get("members"));
    assertEquals(List.of(7_500, 2_500), pool.get("splitsBps"));
    assertEquals(12_000_000L, pool.get("totalAtomic"));
    assertEquals(4_000_000L, pool.get("syncTotalAtomic"));
    assertEquals(null, SongRoyaltyIxBuilder.decodePool(null));
  }

  @Test
  void activateUsesExplicitThenSplitRowsThenBandEqual() {
    Map<String, Object> explicit =
        OnchainCalls.buildActivatePool(service(), 
                7L,
                Map.of(
                    "walletPubkey", OWNER,
                    "splits", List.of(Map.of("musicianProfileId", 12, "bps", 4_000), Map.of("musicianProfileId", 11, "bps", 6_000))));
    assertEquals(List.of(B, A), explicit.get("memberWallets"));
    assertEquals(List.of(4_000, 6_000), explicit.get("splitsBps"));
    assertEquals(PROGRAM, explicit.get("agentAuthorityAccount"));

    splits.add(split(11L, 6_000));
    splits.add(split(12L, 2_000));
    splits.add(split(null, 2_000));
    assertEquals(List.of(7_500, 2_500), OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", OWNER)).get("splitsBps"));

    splits.clear();
    for (long id = 11; id <= 13; id++) {
      MusicianProfile p = new MusicianProfile();
      p.setId(id);
      bandProfiles.add(p);
    }
    Map<String, Object> band = OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", OWNER));
    assertEquals(List.of(A, B, C), band.get("memberWallets"));
    assertEquals(List.of(3_333, 3_333, 3_334), band.get("splitsBps"));
  }

  @Test
  void activateEnforcesSignerSplitsProfilesAndState() {
    splits.add(split(11L, 10_000));
    assertEquals(403, statusOf(() -> OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", WAVE))));
    AgentAuthority auth = new AgentAuthority();
    auth.setAgentWalletPubkey(WAVE);
    auth.setPermissions((byte) AgentAuthorityIxBuilder.PERM_WAVE);
    authorizations.add(auth);
    assertEquals(
        SolanaPda.walletPda("agent", WAVE, PROGRAM),
        OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", WAVE)).get("agentAuthorityAccount"));

    assertEquals(
        400,
        statusOf(
            () ->
                OnchainCalls.buildActivatePool(service(), 
                        7L,
                        Map.of("walletPubkey", OWNER, "splits", List.of(Map.of("musicianProfileId", 11, "bps", 9_000))))));
    missingAccounts.add(SolanaPda.walletPda("musician", A, PROGRAM));
    assertEquals(409, statusOf(() -> OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", OWNER))));
    missingAccounts.clear();
    wallets.remove(11L);
    assertEquals(409, statusOf(() -> OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", OWNER))));
    wallets.put(11L, A);
    track.setPoolActivatedAt(LocalDateTime.now());
    assertEquals(409, statusOf(() -> OnchainCalls.buildActivatePool(service(), 7L, Map.of("walletPubkey", OWNER))));
  }

  @Test
  void confirmActivateVerifiesPoolAndStoresSnapshot() {
    splits.add(split(11L, 7_500));
    splits.add(split(12L, 2_500));
    Track out = OnchainCalls.confirmActivatePool(service(), 7L, Map.of("walletPubkey", OWNER, "txSignature", "sigPool"));
    String pool = SongRoyaltyIxBuilder.poolPda(7L, PROGRAM);
    assertEquals("create_royalty_pool", verified.get("instruction"));
    assertEquals(ISolanaTxVerifier.accounts(null, SolanaPda.configPda(PROGRAM), pool), verified.get("accounts"));
    assertEquals(pool, out.getRoyaltyPoolPda());
    assertTrue(out.getPoolSplitsJson().contains("\"wallet\":\"" + A + "\",\"bps\":7500"));
    recorded.add("sigPool");
    track.setPoolActivatedAt(null);
    assertEquals(409, statusOf(() -> OnchainCalls.confirmActivatePool(service(), 7L, Map.of("walletPubkey", OWNER, "txSignature", "sigPool"))));
  }

  @Test
  void depositNeedsActivePoolThenVerifiesPerSongAccounts() {
    assertEquals(409, statusOf(() -> OnchainCalls.buildDeposit(service(), 3L, Map.of("walletPubkey", OWNER))));
    activate();
    Map<String, Object> ix = OnchainCalls.buildDeposit(service(), 3L, Map.of("walletPubkey", OWNER));
    assertEquals(50_000_000L, ix.get("amountAtomic"));
    assertEquals(List.of(A, B), ix.get("memberWallets"));
    assertEquals(403, statusOf(() -> OnchainCalls.buildDeposit(service(), 3L, Map.of("walletPubkey", C))));
    RoyaltyDeposit confirmed = OnchainCalls.confirmDeposit(service(), 3L, Map.of("walletPubkey", OWNER, "txSignature", "sigDep"));
    assertEquals("deposit_royalties", verified.get("instruction"));
    assertEquals(ix.get("dataHex"), verified.get("dataHex"));
    assertEquals(SongRoyaltyIxBuilder.poolPda(7L, PROGRAM), confirmed.getRoyaltyPoolPda());
    deposit.setOnChainStatus("confirmed");
    assertEquals(409, statusOf(() -> OnchainCalls.buildDeposit(service(), 3L, Map.of("walletPubkey", OWNER))));
  }

  @Test
  void createDealSplitsEightyTwentyAndValidates() {
    SyncLicenseDeal in = new SyncLicenseDeal();
    in.setTrackId(7L);
    in.setLicenseeName("  Netflix Mexico ");
    in.setAmountUsdc(new BigDecimal("10.03"));
    SyncLicenseDeal out = service().createDeal(in);
    assertEquals("Netflix Mexico", out.getLicenseeName());
    assertEquals(new BigDecimal("2.00"), out.getEh8sUsdc());
    assertEquals(new BigDecimal("8.03"), out.getArtistUsdc());
    assertEquals("proposed", out.getStatus());
    in.setAmountUsdc(new BigDecimal("1.005"));
    assertEquals(400, statusOf(() -> service().createDeal(in)));
    in.setAmountUsdc(new BigDecimal("5"));
    in.setLicenseeName(" ");
    assertEquals(400, statusOf(() -> service().createDeal(in)));
  }

  @Test
  void syncPayVerifiesLicensePdaAndMarksPaidOnce() {
    activate();
    Map<String, Object> ix = OnchainCalls.buildPaySync(service(), 9L, Map.of("walletPubkey", C));
    assertEquals(new BigDecimal("80.00"), ix.get("artistUsdc"));
    SyncLicenseDeal paid = OnchainCalls.confirmPaySync(service(), 9L, Map.of("walletPubkey", C, "txSignature", "sigSync"));
    String license = SongRoyaltyIxBuilder.syncPda(7L, 9L, PROGRAM);
    assertEquals(
        ISolanaTxVerifier.accounts(
            null,
            /* #1 */ SolanaPda.configPda(PROGRAM),
            /* #2 */ SongRoyaltyIxBuilder.poolPda(7L, PROGRAM),
            /* #3 */ license),
        verified.get("accounts"));
    assertEquals("paid", paid.getStatus());
    assertEquals(409, statusOf(() -> OnchainCalls.buildPaySync(service(), 9L, Map.of("walletPubkey", C))));
  }

  @Test
  void songCreditsSumWalletShareOfDepositsAndSync() {
    activate();
    deposit.setOnChainStatus("confirmed");
    deposit.setDepositedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
    deal.setStatus("paid");
    deal.setPaidAt(LocalDateTime.of(2026, 9, 2, 10, 0));
    List<Map<String, Object>> credits = service().songCredits(B);
    assertEquals(2, credits.size());
    assertEquals("sync", credits.get(0).get("source"));
    assertEquals(new BigDecimal("20.000000"), credits.get(0).get("creditUsdc"));
    assertEquals(new BigDecimal("12.500000"), credits.get(1).get("creditUsdc"));
    assertEquals(0, service().songCredits(C).size());
    assertEquals(400, statusOf(() -> service().songCredits(" ")));
  }

  private void activate() {
    track.setPoolActivatedAt(LocalDateTime.now());
    track.setRoyaltyPoolPda(SongRoyaltyIxBuilder.poolPda(7L, PROGRAM));
    track.setPoolSplitsJson(
        "[{\"musicianProfileId\":11,\"wallet\":\"" + A + "\",\"bps\":7500},"
            + "{\"musicianProfileId\":12,\"wallet\":\"" + B + "\",\"bps\":2500}]");
  }

  private SongRoyaltyService service() {
    ISongRoyaltyRepository repo = mock(ISongRoyaltyRepository.class);
    ChainConfig cfg = new ChainConfig();
    cfg.setRpcUrl("https://api.devnet.solana.com");
    cfg.setProgramIdDevnet(PROGRAM);
    cfg.setUsdcMint(MINT);
    cfg.setOwnerWalletPubkey(OWNER);
    when(repo.findActiveChainConfig()).thenReturn(Optional.of(cfg));
    when(repo.findTrack(7L)).thenReturn(Optional.of(track));
    when(repo.findSplits(7L)).thenReturn(splits);
    when(repo.findDeposit(3L)).thenReturn(Optional.of(deposit));
    when(repo.findDeal(9L)).thenReturn(Optional.of(deal));
    when(repo.isSignatureRecorded(anyString())).thenAnswer(i -> recorded.contains(i.getArgument(0)));
    when(repo.findActivatedTracks()).thenAnswer(i -> track.getPoolActivatedAt() == null ? List.of() : List.of(track));
    when(repo.findConfirmedDeposits(anyList()))
        .thenAnswer(i -> "confirmed".equals(deposit.getOnChainStatus()) && ((List<?>) i.getArgument(0)).contains(7L) ? List.of(deposit) : List.of());
    when(repo.findPaidDeals(anyList()))
        .thenAnswer(i -> "paid".equals(deal.getStatus()) && ((List<?>) i.getArgument(0)).contains(7L) ? List.of(deal) : List.of());
    when(repo.markPoolActivated(eq(7L), anyString(), anyString(), anyString()))
        .thenAnswer(
            i -> {
              track.setRoyaltyPoolPda(i.getArgument(1));
              track.setPoolSplitsJson(i.getArgument(2));
              track.setPoolTxSignature(i.getArgument(3));
              track.setPoolActivatedAt(LocalDateTime.now());
              return track;
            });
    when(repo.markDepositConfirmed(eq(3L), anyString(), anyString(), anyString()))
        .thenAnswer(
            i -> {
              deposit.setRoyaltyPoolPda(i.getArgument(3));
              deposit.setOnChainStatus("confirmed");
              return deposit;
            });
    when(repo.markDealPaid(eq(9L), anyString(), anyString(), anyString()))
        .thenAnswer(
            i -> {
              deal.setStatus("paid");
              deal.setSyncLicensePda(i.getArgument(2));
              return deal;
            });
    when(repo.insertDeal(any())).thenAnswer(i -> i.getArgument(0));

    IBandVaultRepository band = mock(IBandVaultRepository.class);
    when(band.findMemberProfiles(2L)).thenReturn(bandProfiles);
    when(band.findProfilesByIds(anyList()))
        .thenAnswer(
            i -> {
              List<MusicianProfile> out = new ArrayList<>();
              for (Object id : (List<?>) i.getArgument(0)) {
                MusicianProfile p = new MusicianProfile();
                p.setId((Long) id);
                p.setAccountId((Long) id + 1000);
                out.add(p);
              }
              return out;
            });
    when(band.findAccountsByIds(anyList()))
        .thenAnswer(
            i -> {
              List<Account> out = new ArrayList<>();
              for (Object id : (List<?>) i.getArgument(0)) {
                Long profileId = (Long) id - 1000;
                if (wallets.containsKey(profileId)) {
                  Account a = new Account();
                  a.setId((Long) id);
                  a.setWalletPubkey(wallets.get(profileId));
                  out.add(a);
                }
              }
              return out;
            });

    IAgentAuthorityRepository agents = mock(IAgentAuthorityRepository.class);
    when(agents.findLatestAuthorization(anyString()))
        .thenAnswer(
            i -> authorizations.stream().filter(a -> a.getAgentWalletPubkey().equals(i.getArgument(0))).findFirst());

    SolanaRpcClient rpc = mock(SolanaRpcClient.class);
    when(rpc.getAccountData(anyString(), anyString()))
        .thenAnswer(
            i -> {
              String address = i.getArgument(1);
              if (address.equals(SongRoyaltyIxBuilder.poolPda(7L, PROGRAM)) || missingAccounts.contains(address)) {
                return null;
              }
              return new byte[8];
            });

    ISolanaTxVerifier verifier = mock(ISolanaTxVerifier.class);
    doAnswer(
            i -> {
              verified.put("instruction", i.getArgument(3));
              verified.put("dataHex", i.getArgument(4));
              verified.put("accounts", i.getArgument(6));
              return null;
            })
        .when(verifier)
        .verify(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());

    return new SongRoyaltyService(
        repo, band, agents, new SongRoyaltyIxBuilder(), verifier, rpc, new ObjectMapper());
  }

  private static RoyaltySplit split(Long profileId, int bps) {
    RoyaltySplit s = new RoyaltySplit();
    s.setTrackId(7L);
    s.setMusicianProfileId(profileId);
    s.setParty(profileId == null ? "label" : "musician");
    s.setShareBps(bps);
    return s;
  }

  private static byte[] poolAccount(long trackId, long total, long syncTotal) {
    ByteBuffer buf = ByteBuffer.allocate(8 + 8 + 4 + 64 + 4 + 4 + 8 + 8 + 1).order(ByteOrder.LITTLE_ENDIAN);
    buf.put(new byte[8]).putLong(trackId).putInt(2).put(SolanaPda.decode(A)).put(SolanaPda.decode(B));
    buf.putInt(2).putShort((short) 7_500).putShort((short) 2_500).putLong(total).putLong(syncTotal).put((byte) 254);
    return buf.array();
  }

  private static String disc(String name) {
    return hex(SettleClaimIxBuilder.anchorDiscriminator(name));
  }

  private static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  private static int statusOf(org.junit.jupiter.api.function.Executable call) {
    return assertThrows(ResponseStatusException.class, call).getStatusCode().value();
  }
}
