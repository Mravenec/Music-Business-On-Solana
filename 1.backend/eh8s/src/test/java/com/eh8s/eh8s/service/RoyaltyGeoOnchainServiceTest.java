package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.repository.interfaces.IRoyaltyGeoOnchainRepository;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SolanaTxVerifier;
import com.eh8s.eh8s.service.solana.SubscribeGeographicIxBuilder;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for subscribe_geographic(geo_code, tier, months) build, PDA checks,
 * and zone expiry reads.
 */
class RoyaltyGeoOnchainServiceTest {

  private static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  private static final String WALLET = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";

  private final GeographicSubscription geoSub = new GeographicSubscription();
  private final GeoTier tier = new GeoTier();
  private byte[] accountData;

  RoyaltyGeoOnchainServiceTest() {
    geoSub.setId(1L);
    geoSub.setGeoTierId(5L);
    geoSub.setGeoCode("MEX_CDMX");
    geoSub.setMonths((byte) 2);
    tier.setId(5L);
    tier.setUsdcMonthly(new BigDecimal("99.00"));
    tier.setOnchainTier((byte) 5);
  }

  @Test
  void buildGeoEncodesGeoCodeTierAndMonths() {
    Map<String, Object> ix =
        OnchainCalls.buildSubscribeGeographic(service(), 1L, Map.of("walletPubkey", WALLET, "amountUsdc", "1.00"));
    String disc =
        HexFormat.of().formatHex(SubscribeGeographicIxBuilder.anchorDiscriminator("subscribe_geographic"));
    String code = HexFormat.of().formatHex("MEX_CDMX".getBytes(StandardCharsets.UTF_8));
    assertEquals(disc + "08000000" + code + "0502", ix.get("dataHex"));
    assertEquals(new BigDecimal("198.000000"), ix.get("amountUsdc"));
    assertEquals(
        SolanaPda.findProgramAddress(
            List.of(SolanaPda.seed("geo_sub"), SolanaPda.decode(WALLET), SolanaPda.seed("MEX_CDMX")),
            PROGRAM),
        ix.get("geographicSubscriptionPda"));
  }

  @Test
  void buildGeoRejectsMissingGeoCodeOrTierCode() {
    geoSub.setGeoCode(null);
    assertEquals(409, statusOf(() -> OnchainCalls.buildSubscribeGeographic(service(), 1L, Map.of("walletPubkey", WALLET))));
    geoSub.setGeoCode("MEX_CDMX");
    tier.setOnchainTier(null);
    assertEquals(409, statusOf(() -> OnchainCalls.buildSubscribeGeographic(service(), 1L, Map.of("walletPubkey", WALLET))));
  }

  @Test
  void confirmGeoRejectsPdaWithoutGeoCodeSeed() {
    assertEquals(
        400,
        statusOf(
            () ->
                OnchainCalls.confirmSubscribeGeographic(service(), 
                        1L,
                        Map.of(
                            "walletPubkey", WALLET,
                            "txSignature", "sig",
                            "geographicSubscriptionPda", SolanaPda.walletPda("geo_sub", WALLET, PROGRAM)))));
  }

  @Test
  void zoneStatusReadsExpiryAndValidatesCode() {
    long future = LocalDateTime.now(ZoneOffset.UTC).plusDays(30).toEpochSecond(ZoneOffset.UTC);
    accountData = geoAccount("MEX_CDMX", future);
    Map<String, Object> status = service().zoneStatus(WALLET, "MEX_CDMX");
    assertEquals(true, status.get("active"));
    assertEquals(LocalDateTime.ofEpochSecond(future, 0, ZoneOffset.UTC), status.get("expiresAt"));
    accountData = null;
    assertEquals(false, service().zoneStatus(WALLET, "MEX_CDMX").get("exists"));
    assertEquals(400, statusOf(() -> service().zoneStatus(WALLET, "mex-cdmx")));
  }

  @Test
  void onchainGeoStatusRequiresConfirmedPayer() {
    assertEquals(409, statusOf(() -> service().onchainGeoStatus(1L)));
    geoSub.setPayerWalletPubkey(WALLET);
    accountData = geoAccount("MEX_CDMX", 1_000L);
    assertEquals(false, service().onchainGeoStatus(1L).get("active"));
  }

  /** Anchor GeographicSubscription bytes: disc, authority, geo_code, tier, months_paid, amount, started, expires, active, bump. */
  private static byte[] geoAccount(String code, long expiresAt) {
    byte[] c = code.getBytes(StandardCharsets.UTF_8);
    ByteBuffer buf =
        ByteBuffer.allocate(8 + 32 + 4 + c.length + 1 + 2 + 8 + 8 + 8 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN);
    buf.put(new byte[8]).put(new byte[32]).putInt(c.length).put(c).put((byte) 5).putShort((short) 2);
    buf.putLong(198_000_000L).putLong(1_000L).putLong(expiresAt).put((byte) 1).put((byte) 253);
    return buf.array();
  }

  private static int statusOf(org.junit.jupiter.api.function.Executable call) {
    return assertThrows(ResponseStatusException.class, call).getStatusCode().value();
  }

  @Test
  void confirmGeoRejectsMissingSignature() {
    RoyaltyGeoOnchainService service = service();
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                OnchainCalls.confirmSubscribeGeographic(service, 
                    1L,
                    Map.of(
                        "walletPubkey",
                        "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC",
                        "geographicSubscriptionPda",
                        "GeoPda1111111111111111111111111111111111111")));
    assertEquals(400, ex.getStatusCode().value());
  }

  private RoyaltyGeoOnchainService service() {
    SolanaRpcClient rpc =
        new SolanaRpcClient() {
          @Override
          public byte[] getAccountData(String rpcUrl, String address) {
            return accountData;
          }
        };
    return new RoyaltyGeoOnchainService(
        repo(),
        null,
        new SubscribeGeographicIxBuilder(),
        new SolanaTxVerifier(new SolanaRpcClient()),
        rpc);
  }

  private IRoyaltyGeoOnchainRepository repo() {
    return new IRoyaltyGeoOnchainRepository() {
      @Override
      public Optional<GeographicSubscription> findGeoSubscription(Long subscriptionId) {
        return Optional.of(geoSub);
      }

      @Override
      public Optional<GeoTier> findGeoTier(Long geoTierId) {
        return Optional.of(tier);
      }

      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        ChainConfig cfg = new ChainConfig();
        cfg.setRpcUrl("https://api.devnet.solana.com");
        cfg.setProgramIdDevnet("GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG");
        cfg.setUsdcMint("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU");
        cfg.setOwnerWalletPubkey("7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC");
        return Optional.of(cfg);
      }

      @Override
      public GeographicSubscription markGeoConfirmed(
          Long subscriptionId,
          String walletPubkey,
          String txSignature,
          String geographicSubscriptionPda,
          LocalDateTime onchainExpiresAt) {
        throw new UnsupportedOperationException("not used");
      }
    };
  }
}
