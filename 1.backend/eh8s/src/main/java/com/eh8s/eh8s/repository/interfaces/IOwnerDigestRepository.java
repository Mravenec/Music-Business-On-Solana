package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ persistence for the Claude owner digest: live platform metrics and stored digests.
 */
public interface IOwnerDigestRepository {

  /**
   * Active DevNet chain config (owner wallet source).
   *
   * @return active row when present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Counts every academy subscription.
   *
   * @return row count
   */
  int countAcademySubscriptions();

  /**
   * Counts academy subscriptions paid at or after a moment.
   *
   * @param since window start
   * @return row count
   */
  int countAcademyPaidSince(LocalDateTime since);

  /**
   * Counts academy subscriptions whose on-chain status is {@code confirmed}.
   *
   * @return row count
   */
  int countAcademyConfirmed();

  /**
   * Counts every concert settlement.
   *
   * @return row count
   */
  int countSettlements();

  /**
   * Counts concert settlements whose on-chain status is {@code confirmed}.
   *
   * @return row count
   */
  int countSettlementsConfirmed();

  /**
   * Distinct pending_claim statuses present, sorted.
   *
   * @return statuses
   */
  List<String> findClaimStatuses();

  /**
   * Counts pending claims with one status.
   *
   * @param status pending_claim status
   * @return row count
   */
  int countClaims(String status);

  /**
   * Counts bands with an activated on-chain vault.
   *
   * @return row count
   */
  int countActiveBandVaults();

  /**
   * Counts agent events created at or after a moment.
   *
   * @param since window start
   * @return row count
   */
  int countAgentEventsSince(LocalDateTime since);

  /**
   * Stores one digest.
   *
   * @param digest row without id
   * @return stored row with id
   */
  OwnerDigest insert(OwnerDigest digest);

  /**
   * Newest stored digest.
   *
   * @return latest row when any exists
   */
  Optional<OwnerDigest> latest();
}
