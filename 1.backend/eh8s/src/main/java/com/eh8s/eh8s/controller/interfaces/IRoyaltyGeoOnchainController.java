package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import java.util.Map;

/**
 * HTTP contract for DevNet deposit_royalties and subscribe_geographic build/confirm.
 */
public interface IRoyaltyGeoOnchainController {

  /**
   * Builds per-song deposit_royalties (track pool must be activated) for wallet signing.
   *
   * @param depositId royalty_deposit id
   * @param body walletPubkey (owner or WAVE agent)
   * @return instruction payload
   */
  Map<String, Object> buildDeposit(Long depositId, Map<String, Object> body);

  /**
   * Confirms a DevNet tx and marks the deposit confirmed.
   *
   * @param depositId royalty_deposit id
   * @param body walletPubkey and txSignature (pool PDA is derived from the track)
   * @return updated deposit
   */
  RoyaltyDeposit confirmDeposit(Long depositId, Map<String, Object> body);

  /**
   * Builds subscribe_geographic instruction metadata for wallet signing.
   *
   * @param subscriptionId geographic_subscription id
   * @param body wallet and optional ATA fields
   * @return instruction payload
   */
  Map<String, Object> buildGeo(Long subscriptionId, Map<String, Object> body);

  /**
   * Confirms a DevNet tx and marks the geo subscription paid.
   *
   * @param subscriptionId geographic_subscription id
   * @param body walletPubkey, txSignature, geographicSubscriptionPda
   * @return updated subscription
   */
  GeographicSubscription confirmGeo(Long subscriptionId, Map<String, Object> body);

  /**
   * Reads a confirmed geo subscription's expiry live from DevNet.
   *
   * @param subscriptionId confirmed geographic_subscription id
   * @return exists, expiresAt, active
   */
  Map<String, Object> geoOnchainStatus(Long subscriptionId);

  /**
   * Checks a wallet's zone subscription on DevNet.
   *
   * @param geoCode zone seed
   * @param wallet payer wallet
   * @return exists, expiresAt, active
   */
  Map<String, Object> zoneStatus(String geoCode, String wallet);
}
