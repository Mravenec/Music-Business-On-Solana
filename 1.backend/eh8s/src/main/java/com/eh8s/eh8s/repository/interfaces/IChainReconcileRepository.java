package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.util.List;
import java.util.Optional;

/**
 * Loads confirmed money rows for chain reconcile and demotes orphans. Never invents USDC amounts.
 */
public interface IChainReconcileRepository {

  /**
   * Active chain config (RPC URL + owner wallet).
   *
   * @return config when present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Concert settlements marked confirmed on-chain.
   *
   * @return confirmed settlements
   */
  List<ConcertSettlement> listConfirmedSettlements();

  /**
   * Pending claims marked confirmed on-chain.
   *
   * @return confirmed claims
   */
  List<PendingClaim> listConfirmedClaims();

  /**
   * Academy subscriptions marked confirmed on-chain.
   *
   * @return confirmed academy subscriptions
   */
  List<AcademySubscription> listConfirmedAcademySubscriptions();

  /**
   * Geographic subscriptions marked confirmed on-chain.
   *
   * @return confirmed geo subscriptions
   */
  List<GeographicSubscription> listConfirmedGeoSubscriptions();

  /**
   * Royalty deposits marked confirmed on-chain.
   *
   * @return confirmed deposits
   */
  List<RoyaltyDeposit> listConfirmedRoyaltyDeposits();

  /**
   * Sync license deals marked paid (USDC paid truth for the studio ledger).
   *
   * @return paid deals
   */
  List<SyncLicenseDeal> listPaidSyncLicenseDeals();

  /**
   * Demotes a confirmed settlement to {@code orphaned} when still confirmed. Does not change USDC.
   *
   * @param id settlement id
   * @return true when a row was updated
   */
  boolean demoteSettlement(Long id);

  /**
   * Demotes a confirmed claim to {@code orphaned} when still confirmed. Does not change USDC.
   *
   * @param id claim id
   * @return true when a row was updated
   */
  boolean demoteClaim(Long id);

  /**
   * Demotes a confirmed academy subscription to {@code orphaned} when still confirmed.
   *
   * @param id subscription id
   * @return true when a row was updated
   */
  boolean demoteAcademySubscription(Long id);

  /**
   * Demotes a confirmed geo subscription to {@code orphaned} when still confirmed.
   *
   * @param id subscription id
   * @return true when a row was updated
   */
  boolean demoteGeoSubscription(Long id);

  /**
   * Demotes a confirmed royalty deposit to {@code orphaned} when still confirmed.
   *
   * @param id deposit id
   * @return true when a row was updated
   */
  boolean demoteRoyaltyDeposit(Long id);

  /**
   * Demotes a paid sync license deal to {@code orphaned} when still paid. Does not change USDC.
   *
   * @param id deal id
   * @return true when a row was updated
   */
  boolean demoteSyncLicenseDeal(Long id);
}
