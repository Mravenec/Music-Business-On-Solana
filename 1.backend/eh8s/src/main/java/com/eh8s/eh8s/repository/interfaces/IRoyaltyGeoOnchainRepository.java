package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence for DevNet subscribe_geographic confirmation (per-song deposits: ISongRoyaltyRepository).
 */
public interface IRoyaltyGeoOnchainRepository {

  /**
   * Loads a geographic subscription by id.
   *
   * @param subscriptionId row id
   * @return subscription when present
   */
  Optional<GeographicSubscription> findGeoSubscription(Long subscriptionId);

  /**
   * Loads a geo tier by id.
   *
   * @param geoTierId row id
   * @return tier when present
   */
  Optional<GeoTier> findGeoTier(Long geoTierId);

  /**
   * Loads the active chain_config row.
   *
   * @return active config when present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Marks a geo subscription paid after a confirmed DevNet subscribe_geographic signature.
   *
   * @param subscriptionId row id
   * @param walletPubkey payer wallet
   * @param txSignature confirmed DevNet signature
   * @param geographicSubscriptionPda GeographicSubscription PDA ([geo_sub, wallet, geo_code])
   * @param onchainExpiresAt expires_at read from the PDA after confirm (null when unreadable)
   * @return updated subscription
   */
  GeographicSubscription markGeoConfirmed(
      Long subscriptionId,
      String walletPubkey,
      String txSignature,
      String geographicSubscriptionPda,
      LocalDateTime onchainExpiresAt);
}
