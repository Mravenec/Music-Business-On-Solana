package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import java.util.Map;

/**
 * Builds and verifies DevNet subscribe_academy payments for academy subscriptions.
 */
public interface IAcademyOnchainService {

  /**
   * Builds subscribe_academy(plan_type, months) instruction metadata for a wallet to sign on DevNet.
   * The amount is plan price x months from academy_plan, never from the client.
   *
   * @param subscriptionId academy_subscription id
   * @param walletPubkey signer wallet (base58)
   * @param instructorUsdcAta optional instructor USDC token account override
   * @param payerUsdcAta optional payer USDC token account override
   * @return instruction payload
   */
  Map<String, Object> buildSubscribeAcademy(Long subscriptionId,String walletPubkey, String instructorUsdcAta, String payerUsdcAta);

  /**
   * Confirms a DevNet signature via RPC and marks the subscription paid. Rejects blank signatures.
   *
   * @param subscriptionId academy_subscription id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @param academySubscriptionPda academy subscription PDA created by the transaction
   * @return updated subscription with on_chain_status confirmed
   */
  AcademySubscription confirmSubscribeAcademy(Long subscriptionId,String walletPubkey, String txSignature, String academySubscriptionPda);

  /**
   * Reads the payer's AcademySubscription PDA live from DevNet (expiry + active).
   *
   * @param subscriptionId confirmed academy_subscription id
   * @return subscriptionId, academySubscriptionPda, exists, expiresAt (UTC), active
   */
  Map<String, Object> onchainStatus(Long subscriptionId);
}
