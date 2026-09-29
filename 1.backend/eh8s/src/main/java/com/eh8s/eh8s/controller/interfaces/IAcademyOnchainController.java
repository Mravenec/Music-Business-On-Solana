package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import java.util.Map;

/**
 * HTTP contract for DevNet subscribe_academy build and confirm.
 */
public interface IAcademyOnchainController {

  /**
   * Builds subscribe_academy instruction metadata for wallet signing.
   *
   * @param subscriptionId academy_subscription id
   * @param body wallet and optional ATA fields
   * @return instruction payload
   */
  Map<String, Object> build(Long subscriptionId, Map<String, Object> body);

  /**
   * Confirms a DevNet tx and marks the subscription paid.
   *
   * @param subscriptionId academy_subscription id
   * @param body walletPubkey, txSignature, academySubscriptionPda
   * @return updated subscription
   */
  AcademySubscription confirm(Long subscriptionId, Map<String, Object> body);

  /**
   * Reads the AcademySubscription PDA expiry live from DevNet.
   *
   * @param subscriptionId confirmed academy_subscription id
   * @return exists, expiresAt, active
   */
  Map<String, Object> onchainStatus(Long subscriptionId);
}
