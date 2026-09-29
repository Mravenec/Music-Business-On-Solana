package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the owner treasury screen: chain config, verified fee inflows, and withdrawals.
 */
public interface ITreasuryRepository {

  /**
   * Active DevNet chain config (program id, mint, RPC, owner wallet).
   *
   * @return config when present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Newest academy subscriptions paid on-chain (the 85% treasury leg is {@code treasuryUsdc}).
   *
   * @param limit max rows
   * @return rows with {@code payTxSignature}, newest {@code paidAt} first
   */
  List<AcademySubscription> findPaidAcademySubscriptions(int limit);

  /**
   * Newest geographic subscriptions paid on-chain.
   *
   * @param limit max rows
   * @return rows with {@code payTxSignature}, newest {@code paidAt} first
   */
  List<GeographicSubscription> findPaidGeographicSubscriptions(int limit);

  /**
   * Geo tiers by id (the monthly USDC price of a geographic subscription).
   *
   * @param ids geo_tier ids (empty list returns an empty list)
   * @return tiers found (any order)
   */
  List<GeoTier> findGeoTiersByIds(List<Long> ids);

  /**
   * Newest concert settlements confirmed on-chain (the platform leg is {@code eh8sFeeUsdc}).
   *
   * @param limit max rows
   * @return rows with {@code settleTxSignature}, newest {@code settledAt} first
   */
  List<ConcertSettlement> findSettledConcerts(int limit);

  /**
   * Recorded treasury withdrawals, newest first.
   *
   * @return withdrawal rows
   */
  List<TreasuryWithdrawal> listWithdrawals();

  /**
   * Stores one verified withdrawal. The unique {@code tx_signature} blocks replay.
   *
   * @param withdrawal row to insert (id ignored)
   * @return stored row with id
   */
  TreasuryWithdrawal insertWithdrawal(TreasuryWithdrawal withdrawal);

  /**
   * Whether this signature is already stored on a withdrawal.
   *
   * @param txSignature DevNet transaction signature
   * @return true when a withdrawal row already holds it
   */
  default boolean isRecorded(String txSignature) {
    return false;
  }

  /**
   * Whether a verified {@code init_governance} is recorded (direct withdraw is then refused).
   *
   * @return true once governance exists
   */
  default boolean governanceActive() {
    return false;
  }
}
