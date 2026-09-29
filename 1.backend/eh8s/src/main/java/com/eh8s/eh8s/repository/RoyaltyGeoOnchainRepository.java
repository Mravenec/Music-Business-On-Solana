package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.DevnetPayMarkerRecord;
import com.eh8s.eh8s.repository.interfaces.IRoyaltyGeoOnchainRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.DEVNET_PAY_MARKER;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEOGRAPHIC_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEO_TIER;

/**
 * JOOQ persistence for confirmed geo subscribe DevNet payments.
 */
@Repository
public class RoyaltyGeoOnchainRepository implements IRoyaltyGeoOnchainRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public RoyaltyGeoOnchainRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GeographicSubscription> findGeoSubscription(Long subscriptionId) {
    return dsl.selectFrom(GEOGRAPHIC_SUBSCRIPTION)
        .where(GEOGRAPHIC_SUBSCRIPTION.ID.eq(subscriptionId))
        .fetchOptionalInto(GeographicSubscription.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GeoTier> findGeoTier(Long geoTierId) {
    return dsl.selectFrom(GEO_TIER)
        .where(GEO_TIER.ID.eq(geoTierId))
        .fetchOptionalInto(GeoTier.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<ChainConfig> findActiveChainConfig() {
    return dsl.selectFrom(CHAIN_CONFIG)
        .where(CHAIN_CONFIG.IS_ACTIVE.eq((byte) 1))
        .orderBy(CHAIN_CONFIG.ID)
        .limit(1)
        .fetchOptionalInto(ChainConfig.class);
  }

  /** {@inheritDoc} */
  @Override
  public GeographicSubscription markGeoConfirmed(
      Long subscriptionId,
      String walletPubkey,
      String txSignature,
      String geographicSubscriptionPda,
      LocalDateTime onchainExpiresAt) {
    findGeoSubscription(subscriptionId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown geo subscription"));
    LocalDateTime now = LocalDateTime.now();
    dsl.update(GEOGRAPHIC_SUBSCRIPTION)
        .set(GEOGRAPHIC_SUBSCRIPTION.PAY_TX_SIGNATURE, txSignature)
        .set(GEOGRAPHIC_SUBSCRIPTION.PAID_AT, now)
        .set(GEOGRAPHIC_SUBSCRIPTION.PAYER_WALLET_PUBKEY, walletPubkey)
        .set(GEOGRAPHIC_SUBSCRIPTION.GEOGRAPHIC_SUBSCRIPTION_PDA, geographicSubscriptionPda)
        .set(GEOGRAPHIC_SUBSCRIPTION.ON_CHAIN_STATUS, "confirmed")
        .set(GEOGRAPHIC_SUBSCRIPTION.ONCHAIN_EXPIRES_AT, onchainExpiresAt)
        .set(GEOGRAPHIC_SUBSCRIPTION.INTENDED_INSTRUCTION, "subscribe_geographic")
        .where(GEOGRAPHIC_SUBSCRIPTION.ID.eq(subscriptionId))
        .execute();

    GeographicSubscription sub =
        findGeoSubscription(subscriptionId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update failed"));
    BigDecimal amount =
        findGeoTier(sub.getGeoTierId())
            .map(GeoTier::getUsdcMonthly)
            .orElse(BigDecimal.ZERO);
    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("pay");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(amount);
    marker.setTxSignature(txSignature);
    marker.setStatus("confirmed");
    marker.setIntendedInstruction("subscribe_geographic");
    marker.setNote("Confirmed DevNet subscribe_geographic subscriptionId=" + subscriptionId);
    marker.setRecordedAt(now);
    DevnetPayMarkerRecord rec = dsl.newRecord(DEVNET_PAY_MARKER, marker);
    rec.changed(DEVNET_PAY_MARKER.ID, false);
    rec.store();

    return sub;
  }
}
