package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for DevNet pay/claim ledger markers.
 */
public interface IDevnetPayRepository {

  /**
   * @return all markers newest first
   */
  List<DevnetPayMarker> findAll();

  /**
   * Inserts a ledger marker.
   *
   * @param marker row to store
   * @return stored row with id
   */
  DevnetPayMarker insert(DevnetPayMarker marker);

  /**
   * @return active chain_config if present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Records a claim signature on pending_claim and inserts a claim marker.
   *
   * @param claimId claim primary key
   * @param walletPubkey payer/claimer wallet
   * @param txSignature optional DevNet signature (intended if blank)
   * @return updated claim
   */
  PendingClaim recordClaim(Long claimId, String walletPubkey, String txSignature);

  /**
   * Records a pay signature on academy_subscription and inserts a pay marker.
   *
   * @param subscriptionId subscription primary key
   * @param walletPubkey payer wallet
   * @param txSignature optional DevNet signature
   * @return updated subscription
   */
  AcademySubscription recordPay(Long subscriptionId, String walletPubkey, String txSignature);
}
