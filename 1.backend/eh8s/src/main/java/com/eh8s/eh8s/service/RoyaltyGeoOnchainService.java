package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.ISubscribeGeographicIxBuilder;
import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.repository.interfaces.IRoyaltyGeoOnchainRepository;
import com.eh8s.eh8s.service.interfaces.IRoyaltyGeoOnchainService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.interfaces.ISongRoyaltyService;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.SubscribeAcademyIxBuilder;
import com.eh8s.eh8s.service.solana.SubscribeGeographicIxBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * DevNet subscribe_geographic build + RPC confirm; deposit_royalties delegates to the per-song pool
 * service. Never marks paid without a confirmed signature.
 */
@Service
public class RoyaltyGeoOnchainService implements IRoyaltyGeoOnchainService {

  private final IRoyaltyGeoOnchainRepository royaltyGeoOnchainRepository;
  private final ISongRoyaltyService songRoyaltyService;
  private final ISubscribeGeographicIxBuilder geoIxBuilder;
  private final ISolanaTxVerifier txVerifier;
  private final ISolanaRpcClient rpc;

  /**
   * Creates the service.
   *
   * @param royaltyGeoOnchainRepository royalty/geo persistence
   * @param songRoyaltyService per-song deposit_royalties build/confirm
   * @param geoIxBuilder geo subscribe instruction builder
   * @param txVerifier DevNet transaction verifier
   * @param rpc DevNet RPC (GeographicSubscription account reads)
   */
  public RoyaltyGeoOnchainService(
      IRoyaltyGeoOnchainRepository royaltyGeoOnchainRepository,
      ISongRoyaltyService songRoyaltyService,
      ISubscribeGeographicIxBuilder geoIxBuilder,
      ISolanaTxVerifier txVerifier,
      ISolanaRpcClient rpc) {
    this.royaltyGeoOnchainRepository = royaltyGeoOnchainRepository;
    this.songRoyaltyService = songRoyaltyService;
    this.geoIxBuilder = geoIxBuilder;
    this.txVerifier = txVerifier;
    this.rpc = rpc;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildDepositRoyalties(Long depositId, String walletPubkey) {
    return songRoyaltyService.buildDeposit(depositId, walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  public RoyaltyDeposit confirmDepositRoyalties(
      Long depositId, String walletPubkey, String txSignature) {
    return songRoyaltyService.confirmDeposit(depositId, walletPubkey, txSignature);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildSubscribeGeographic(
      Long subscriptionId, String walletPubkey, String payerUsdcAta) {
    GeographicSubscription sub = requireGeoSubscription(subscriptionId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    GeoPriced priced = priceGeo(sub);
    Map<String, Object> ix =
        geoIxBuilder.build(
            cfg.getProgramIdDevnet(),
            cfg.getUsdcMint(),
            cfg.getRpcUrl(),
            wallet,
            priced.geoCode(),
            priced.tier(),
            priced.months(),
            priced.amount(),
            optionalString(payerUsdcAta));
    ix.put("subscriptionId", subscriptionId);
    ix.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public GeographicSubscription confirmSubscribeGeographic(
      Long subscriptionId, String walletPubkey, String rawTxSignature, String geographicSubscriptionPda) {
    GeographicSubscription sub = requireGeoSubscription(subscriptionId);
    ChainConfig cfg = requireChainConfig();
    String wallet = requiredString(walletPubkey, "walletPubkey");
    String txSignature = requiredString(rawTxSignature, "rawTxSignature");
    String pda = requiredString(geographicSubscriptionPda, "geographicSubscriptionPda");
    String programId = cfg.getProgramIdDevnet();
    GeoPriced priced = priceGeo(sub);
    String derived = SubscribeGeographicIxBuilder.geoPda(wallet, priced.geoCode(), programId);
    if (!derived.equals(pda)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "geographicSubscriptionPda does not belong to this payer wallet and geo_code");
    }
    Map<String, Object> expected =
        geoIxBuilder.build(
            programId,
            cfg.getUsdcMint(),
            cfg.getRpcUrl(),
            wallet,
            priced.geoCode(),
            priced.tier(),
            priced.months(),
            priced.amount(),
            null);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "subscribe_geographic",
        (String) expected.get("dataHex"),
        wallet,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), derived));
    return royaltyGeoOnchainRepository.markGeoConfirmed(
        subscriptionId, wallet, txSignature, pda, readGeoExpiry(cfg.getRpcUrl(), derived));
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> onchainGeoStatus(Long subscriptionId) {
    GeographicSubscription sub = requireGeoSubscription(subscriptionId);
    ChainConfig cfg = requireChainConfig();
    if (blank(sub.getPayerWalletPubkey()) || blank(sub.getGeoCode())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Geo subscription is not confirmed on DevNet yet");
    }
    return zoneStatus(sub.getPayerWalletPubkey(), sub.getGeoCode(), cfg, subscriptionId);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> zoneStatus(String walletPubkey, String geoCode) {
    if (blank(walletPubkey) || geoCode == null
        || !SubscribeGeographicIxBuilder.GEO_CODE.matcher(geoCode).matches()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "walletPubkey and a valid geoCode (A-Z 0-9 _) are required");
    }
    return zoneStatus(walletPubkey, geoCode, requireChainConfig(), null);
  }

  private Map<String, Object> zoneStatus(
      String wallet, String geoCode, ChainConfig cfg, Long subscriptionId) {
    String pda = SubscribeGeographicIxBuilder.geoPda(wallet, geoCode, cfg.getProgramIdDevnet());
    byte[] data = rpc.getAccountData(cfg.getRpcUrl(), pda);
    LocalDateTime expiresAt = SubscribeGeographicIxBuilder.decodeExpiresAt(data);
    Map<String, Object> out = new LinkedHashMap<>();
    if (subscriptionId != null) {
      out.put("subscriptionId", subscriptionId);
    }
    out.put("walletPubkey", wallet);
    out.put("geoCode", geoCode);
    out.put("geographicSubscriptionPda", pda);
    out.put("exists", data != null);
    out.put("expiresAt", expiresAt);
    out.put("active", expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC)));
    return out;
  }

  private GeoPriced priceGeo(GeographicSubscription sub) {
    GeoTier tier =
        royaltyGeoOnchainRepository
            .findGeoTier(sub.getGeoTierId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Unknown geo tier"));
    if (tier.getOnchainTier() == null || tier.getUsdcMonthly() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Geo tier has no on-chain tier code");
    }
    String geoCode = sub.getGeoCode();
    if (geoCode == null || !SubscribeGeographicIxBuilder.GEO_CODE.matcher(geoCode).matches()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Geo subscription has no valid geo_code");
    }
    int months = sub.getMonths() == null ? 1 : sub.getMonths();
    if (months < 1 || months > SubscribeAcademyIxBuilder.MAX_MONTHS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription months must be 1..12");
    }
    return new GeoPriced(
        geoCode, tier.getOnchainTier(), months, tier.getUsdcMonthly().multiply(BigDecimal.valueOf(months)));
  }

  private LocalDateTime readGeoExpiry(String rpcUrl, String pda) {
    try {
      return SubscribeGeographicIxBuilder.decodeExpiresAt(rpc.getAccountData(rpcUrl, pda));
    } catch (ResponseStatusException ex) {
      return null;
    }
  }

  private GeographicSubscription requireGeoSubscription(Long subscriptionId) {
    return royaltyGeoOnchainRepository
        .findGeoSubscription(subscriptionId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown geo subscription"));
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        royaltyGeoOnchainRepository
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

  private record GeoPriced(String geoCode, int tier, int months, BigDecimal amount) {}
}
