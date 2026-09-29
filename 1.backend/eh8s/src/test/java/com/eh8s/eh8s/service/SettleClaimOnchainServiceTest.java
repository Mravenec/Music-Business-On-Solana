package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.repository.interfaces.IBandVaultRepository;
import com.eh8s.eh8s.repository.interfaces.ISettleClaimOnchainRepository;
import com.eh8s.eh8s.service.solana.BandVaultIxBuilder;
import com.eh8s.eh8s.service.solana.ClaimRoyaltiesIxBuilder;
import com.eh8s.eh8s.service.solana.SettleConcertIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SolanaTxVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for settle_concert (BandVault split) / claim_royalties build and signature rejection.
 */
class SettleClaimOnchainServiceTest {

  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String WEIGHTS_JSON =
      "[{\"musicianProfileId\":11,\"wallet\":\"7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC\",\"bps\":3800},"
          + "{\"musicianProfileId\":12,\"wallet\":\"4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU\",\"bps\":3520},"
          + "{\"musicianProfileId\":13,\"wallet\":\"CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o\",\"bps\":2000},"
          + "{\"musicianProfileId\":14,\"wallet\":\"U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq\",\"bps\":680}]";

  @Test
  void buildSettleSplitsNetAcrossVaultMembers() {
    SettleClaimOnchainService service = service(activeBand());
    Map<String, Object> ix = OnchainCalls.buildSettleConcert(service, 1L, Map.of("walletPubkey", OWNER));
    assertEquals("settle_concert", ix.get("instruction"));
    assertTrue(String.valueOf(ix.get("discriminatorHex")).length() == 16);
    // discriminator | concert_id (1) | gross 875_000_000 | expenses 200_000_000 — all u64 LE
    assertEquals(
        ix.get("discriminatorHex") + "0100000000000000" + "c070273400000000" + "00c2eb0b00000000",
        ix.get("dataHex"));
    assertEquals(101_250_000L, ix.get("eh8sFeeAtomic"));
    assertEquals(573_750_000L, ix.get("bandPoolAtomic"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> members = (List<Map<String, Object>>) ix.get("members");
    assertEquals(new BigDecimal("218.025000"), members.get(0).get("pendingUsdc"));
    assertEquals(new BigDecimal("201.960000"), members.get(1).get("pendingUsdc"));
    assertEquals(new BigDecimal("114.750000"), members.get(2).get("pendingUsdc"));
    assertEquals(new BigDecimal("39.015000"), members.get(3).get("pendingUsdc"));
    assertEquals(4, ((List<?>) ix.get("memberWallets")).size());
  }

  @Test
  void buildSettleWithoutVaultIsConflict() {
    Band band = activeBand();
    band.setBandVaultPda(null);
    SettleClaimOnchainService service = service(band);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> OnchainCalls.buildSettleConcert(service, 1L, Map.of("walletPubkey", OWNER)));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void onChainFeeFloorsMicroUnits() {
    assertEquals(101_251_500L, SettleConcertIxBuilder.feeAtomic(675_010_000L, 1500));
    assertEquals(0L, SettleConcertIxBuilder.feeAtomic(1L, 1500));
  }

  @Test
  void splitMembersGivesDustToLastMember() {
    assertEquals(
        List.of(33L, 33L, 34L), SettleConcertIxBuilder.splitMembers(100L, List.of(3333, 3333, 3334)));
    assertEquals(List.of(3L, 3L, 4L), SettleConcertIxBuilder.splitMembers(10L, List.of(3333, 3333, 3334)));
  }

  @Test
  void buildClaimReturnsDiscriminator() {
    SettleClaimOnchainService service = service(activeBand());
    Map<String, Object> ix =
        OnchainCalls.buildClaimRoyalties(service, 
            1L,
            Map.of(
                "walletPubkey",
                OWNER,
                "vaultUsdcAta",
                "VaultUsdc",
                "musicianUsdcAta",
                "MusicianUsdc"));
    assertEquals("claim_royalties", ix.get("instruction"));
    assertTrue(String.valueOf(ix.get("discriminatorHex")).length() == 16);
  }

  @Test
  void confirmClaimRejectsMissingSignature() {
    SettleClaimOnchainService service = service(activeBand());
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                OnchainCalls.confirmClaimRoyalties(service, 
                    1L,
                    Map.of(
                        "walletPubkey",
                        OWNER,
                        "claimPda",
                        "Pda111111111111111111111111111111111111111")));
    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  void confirmSettleRejectsMissingSignature() {
    SettleClaimOnchainService service = service(activeBand());
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                OnchainCalls.confirmSettleConcert(service, 
                    1L,
                    Map.of(
                        "walletPubkey",
                        OWNER,
                        "concertSettlementPda",
                        "Pda111111111111111111111111111111111111111")));
    assertEquals(400, ex.getStatusCode().value());
  }

  static Band activeBand() {
    Band band = new Band();
    band.setId(1L);
    band.setBandVaultPda("BandVaultPda1111111111111111111111111111111");
    band.setSppWeightsJson(WEIGHTS_JSON);
    return band;
  }

  static BandVaultService bandVaultService(Band band) {
    return new BandVaultService(
        bandRepo(band), repo(), new BandVaultIxBuilder(), new SolanaTxVerifier(new SolanaRpcClient()), new ObjectMapper());
  }

  private static SettleClaimOnchainService service(Band band) {
    return new SettleClaimOnchainService(
        repo(),
        bandRepo(band),
        bandVaultService(band),
        new SettleConcertIxBuilder(),
        new ClaimRoyaltiesIxBuilder(),
        new SolanaTxVerifier(new SolanaRpcClient()));
  }

  static IBandVaultRepository bandRepo(Band band) {
    return new IBandVaultRepository() {
      @Override
      public Optional<Band> findBand(Long bandId) {
        return Optional.of(band);
      }

      @Override
      public Optional<Band> findBandByConcert(Long concertId) {
        return Optional.of(band);
      }

      @Override
      public List<MusicianProfile> findMemberProfiles(Long bandId) {
        return List.of();
      }

      @Override
      public List<MusicianProfile> findProfilesByIds(List<Long> musicianProfileIds) {
        return List.of();
      }

      @Override
      public List<com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account> findAccountsByIds(
          List<Long> accountIds) {
        return List.of();
      }

      @Override
      public List<SppMemberScore> findLatestClosedScores(Long bandId) {
        return List.of();
      }

      @Override
      public Band markVaultActivated(
          Long bandId, String bandVaultPda, String txSignature, String weightsJson) {
        throw new UnsupportedOperationException("not used");
      }

      @Override
      public Band markWeightsSynced(Long bandId, String txSignature, String weightsJson) {
        throw new UnsupportedOperationException("not used");
      }
    };
  }

  static ISettleClaimOnchainRepository repo() {
    return new ISettleClaimOnchainRepository() {
      @Override
      public Optional<ConcertSettlement> findSettlement(Long settlementId) {
        ConcertSettlement row = new ConcertSettlement();
        row.setId(settlementId);
        row.setConcertId(1L);
        row.setGrossUsdc(new BigDecimal("875.00"));
        row.setExpensesUsdc(new BigDecimal("200.00"));
        row.setNetUsdc(new BigDecimal("675.00"));
        row.setEh8sFeeUsdc(new BigDecimal("101.25"));
        row.setBandPoolUsdc(new BigDecimal("573.75"));
        row.setStatus("settled");
        return Optional.of(row);
      }

      @Override
      public Optional<PendingClaim> findClaim(Long claimId) {
        PendingClaim row = new PendingClaim();
        row.setId(claimId);
        row.setConcertSettlementId(1L);
        row.setAmountUsdc(new BigDecimal("143.44"));
        row.setStatus("pending");
        return Optional.of(row);
      }

      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        ChainConfig cfg = new ChainConfig();
        cfg.setRpcUrl("https://api.devnet.solana.com");
        cfg.setProgramIdDevnet("GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG");
        cfg.setUsdcMint("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU");
        cfg.setOwnerWalletPubkey(OWNER);
        cfg.setProtocolFeeBps(1500);
        return Optional.of(cfg);
      }

      @Override
      public Optional<String> findFirstClaimWallet(Long settlementId) {
        return Optional.of("MusicianWallet1111111111111111111111111111");
      }

      @Override
      public ConcertSettlement markSettlementConfirmed(
          Long settlementId, String walletPubkey, String txSignature, String concertSettlementPda) {
        throw new UnsupportedOperationException("not used");
      }

      @Override
      public PendingClaim markClaimConfirmed(
          Long claimId, String walletPubkey, String txSignature, String claimPda) {
        throw new UnsupportedOperationException("not used");
      }
    };
  }
}
