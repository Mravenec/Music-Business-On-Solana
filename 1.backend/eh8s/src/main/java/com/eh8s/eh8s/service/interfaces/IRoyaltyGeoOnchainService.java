package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import java.util.Map;

/**
 * Builds and verifies DevNet deposit_royalties and subscribe_geographic payments.
 */
public interface IRoyaltyGeoOnchainService {

  /**
   * Builds per-song deposit_royalties (track pool must be activated) for a wallet to sign.
   *
   * @param depositId royalty_deposit id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildDepositRoyalties(Long depositId,String walletPubkey);

  /**
   * Confirms a DevNet signature via RPC and marks the deposit confirmed. Rejects blank signatures.
   *
   * @param depositId royalty_deposit id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated deposit with on_chain_status confirmed
   */
  RoyaltyDeposit confirmDepositRoyalties(Long depositId,String walletPubkey, String txSignature);

  /**
   * Builds subscribe_geographic instruction metadata for a wallet to sign on DevNet.
   *
   * @param subscriptionId geographic_subscription id
   * @param walletPubkey signer wallet (base58)
   * @param payerUsdcAta optional payer USDC token account override
   * @return instruction payload
   */
  Map<String, Object> buildSubscribeGeographic(Long subscriptionId,String walletPubkey, String payerUsdcAta);

  /**
   * Confirms a DevNet signature via RPC and marks the geo subscription paid. Rejects blank
   * signatures.
   *
   * @param subscriptionId geographic_subscription id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @param geographicSubscriptionPda geographic subscription PDA created by the transaction
   * @return updated subscription with on_chain_status confirmed
   */
  GeographicSubscription confirmSubscribeGeographic(Long subscriptionId,String walletPubkey, String txSignature, String geographicSubscriptionPda);

  /**
   * Reads a confirmed geo subscription's PDA live from DevNet (expiry + active).
   *
   * @param subscriptionId confirmed geographic_subscription id
   * @return subscriptionId, walletPubkey, geoCode, geographicSubscriptionPda, exists, expiresAt, active
   */
  Map<String, Object> onchainGeoStatus(Long subscriptionId);

  /**
   * Checks whether a wallet holds an active zone subscription on DevNet (ATLAS gate).
   *
   * @param walletPubkey payer wallet
   * @param geoCode zone seed (A-Z 0-9 _)
   * @return walletPubkey, geoCode, geographicSubscriptionPda, exists, expiresAt, active
   */
  Map<String, Object> zoneStatus(String walletPubkey, String geoCode);
}
