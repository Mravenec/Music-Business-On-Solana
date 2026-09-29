package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IAcademyOnchainRepository;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SolanaTxVerifier;
import com.eh8s.eh8s.service.solana.SubscribeAcademyIxBuilder;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for subscribe_academy(plan_type, months) build, PDA checks, and expiry reads.
 */
class AcademyOnchainServiceTest {

  private static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  private static final String WALLET = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";

  private final AcademySubscription sub = new AcademySubscription();
  private final AcademyPlan plan =
      new AcademyPlan(2L, "band", "Band", new BigDecimal("55.00"), (byte) 2, "x");
  private byte[] accountData;

  AcademyOnchainServiceTest() {
    sub.setId(1L);
    sub.setAcademyPlanId(2L);
    sub.setMonths((byte) 3);
    sub.setTreasuryUsdc(new BigDecimal("140.25"));
    sub.setInstructorUsdc(new BigDecimal("24.75"));
  }

  @Test
  void buildEncodesPlanAndMonthsWithNewSeed() {
    Map<String, Object> ix =
        OnchainCalls.buildSubscribeAcademy(service(), 1L, Map.of("walletPubkey", WALLET, "amountUsdc", "1.00"));
    String disc = HexFormat.of().formatHex(SubscribeAcademyIxBuilder.anchorDiscriminator("subscribe_academy"));
    assertEquals(disc + "0203", ix.get("dataHex"));
    assertEquals(new BigDecimal("165.000000"), ix.get("amountUsdc"));
    assertEquals(2, ix.get("planType"));
    assertEquals(3, ix.get("months"));
    assertEquals(SolanaPda.walletPda("academy_sub", WALLET, PROGRAM), ix.get("academySubscriptionPda"));
    assertEquals(8500, ix.get("treasuryShareBps"));
  }

  @Test
  void buildRejectsStoredAmountThatIsNotPriceTimesMonths() {
    sub.setMonths((byte) 1);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> OnchainCalls.buildSubscribeAcademy(service(), 1L, Map.of("walletPubkey", WALLET)));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void buildRejectsPlanWithoutOnchainCode() {
    plan.setOnchainPlanType(null);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> OnchainCalls.buildSubscribeAcademy(service(), 1L, Map.of("walletPubkey", WALLET)));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void confirmRejectsOldAcademySeedPda() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                OnchainCalls.confirmSubscribeAcademy(service(), 
                        1L,
                        Map.of(
                            "walletPubkey", WALLET,
                            "txSignature", "sig",
                            "academySubscriptionPda", SolanaPda.walletPda("academy", WALLET, PROGRAM))));
    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  void confirmRejectsMissingSignature() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                OnchainCalls.confirmSubscribeAcademy(service(), 
                        1L,
                        Map.of(
                            "walletPubkey", WALLET,
                            "academySubscriptionPda",
                                SubscribeAcademyIxBuilder.academyPda(WALLET, PROGRAM))));
    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  void onchainStatusReadsExpiryFromThePda() {
    sub.setPayerWalletPubkey(WALLET);
    long future = LocalDateTime.now(ZoneOffset.UTC).plusDays(60).toEpochSecond(ZoneOffset.UTC);
    accountData = academyAccount(future);
    Map<String, Object> status = service().onchainStatus(1L);
    assertEquals(true, status.get("exists"));
    assertEquals(true, status.get("active"));
    assertEquals(
        LocalDateTime.ofEpochSecond(future, 0, ZoneOffset.UTC), status.get("expiresAt"));

    accountData = academyAccount(1_000L);
    assertFalse((Boolean) service().onchainStatus(1L).get("active"));
  }

  @Test
  void onchainStatusRequiresAConfirmedPayer() {
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service().onchainStatus(1L));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void decodeExpiresAtNeedsTheFullLayout() {
    assertTrue(SubscribeAcademyIxBuilder.decodeExpiresAt(new byte[10]) == null);
    assertEquals(
        LocalDateTime.ofEpochSecond(1_700_000_000L, 0, ZoneOffset.UTC),
        SubscribeAcademyIxBuilder.decodeExpiresAt(academyAccount(1_700_000_000L)));
  }

  /** Anchor AcademySubscription bytes: disc, musician, plan, months_paid, amount, started, expires, active, bump. */
  private static byte[] academyAccount(long expiresAt) {
    ByteBuffer buf = ByteBuffer.allocate(8 + 32 + 1 + 2 + 8 + 8 + 8 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN);
    buf.put(new byte[8]).put(new byte[32]).put((byte) 2).putShort((short) 3);
    buf.putLong(165_000_000L).putLong(1_000L).putLong(expiresAt).put((byte) 1).put((byte) 254);
    return buf.array();
  }

  private AcademyOnchainService service() {
    SolanaRpcClient rpc =
        new SolanaRpcClient() {
          @Override
          public byte[] getAccountData(String rpcUrl, String address) {
            return accountData;
          }
        };
    return new AcademyOnchainService(
        repo(), new SubscribeAcademyIxBuilder(), new SolanaTxVerifier(new SolanaRpcClient()), rpc);
  }

  private IAcademyOnchainRepository repo() {
    return new IAcademyOnchainRepository() {
      @Override
      public Optional<AcademySubscription> findSubscription(Long subscriptionId) {
        return Optional.of(sub);
      }

      @Override
      public Optional<AcademyPlan> findPlan(Long planId) {
        return Optional.of(plan);
      }

      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        ChainConfig cfg = new ChainConfig();
        cfg.setRpcUrl("https://api.devnet.solana.com");
        cfg.setProgramIdDevnet(PROGRAM);
        cfg.setUsdcMint("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU");
        cfg.setOwnerWalletPubkey(WALLET);
        return Optional.of(cfg);
      }

      @Override
      public AcademySubscription markConfirmed(
          Long subscriptionId,
          String walletPubkey,
          String txSignature,
          String academySubscriptionPda,
          LocalDateTime onchainExpiresAt) {
        throw new UnsupportedOperationException("not used");
      }
    };
  }
}
