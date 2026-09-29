package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence for DevNet academy subscribe confirmation (tx + PDA linkage).
 */
public interface IAcademyOnchainRepository {

  /**
   * Loads an academy subscription by id.
   *
   * @param subscriptionId row id
   * @return subscription when present
   */
  Optional<AcademySubscription> findSubscription(Long subscriptionId);

  /**
   * Loads an academy plan (price + on-chain plan code) by id.
   *
   * @param planId academy_plan id
   * @return plan when present
   */
  Optional<AcademyPlan> findPlan(Long planId);

  /**
   * Loads the active chain_config row.
   *
   * @return active config when present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Marks a subscription paid after a confirmed DevNet subscribe_academy signature.
   *
   * @param subscriptionId row id
   * @param walletPubkey payer wallet
   * @param txSignature confirmed DevNet signature
   * @param academySubscriptionPda AcademySubscription PDA
   * @param onchainExpiresAt expires_at read from the PDA after confirm (null when unreadable)
   * @return updated subscription
   */
  AcademySubscription markConfirmed(
      Long subscriptionId,
      String walletPubkey,
      String txSignature,
      String academySubscriptionPda,
      LocalDateTime onchainExpiresAt);
}
