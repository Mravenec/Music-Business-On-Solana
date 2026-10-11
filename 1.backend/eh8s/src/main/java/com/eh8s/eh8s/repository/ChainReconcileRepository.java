package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEOGRAPHIC_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.ROYALTY_DEPOSIT;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.SYNC_LICENSE_DEAL;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainReconcileRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * JOOQ persistence for on-demand / scheduled chain reconcile of confirmed money rows.
 */
@Repository
public class ChainReconcileRepository implements IChainReconcileRepository {

  static final String CONFIRMED = "confirmed";
  static final String ORPHANED = "orphaned";
  static final String PAID = "paid";

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public ChainReconcileRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<ChainConfig> findActiveChainConfig() {
    return dsl.selectFrom(CHAIN_CONFIG)
        .where(CHAIN_CONFIG.IS_ACTIVE.eq((byte) 1))
        .orderBy(CHAIN_CONFIG.ID)
        .limit(1)
        .fetchOptionalInto(ChainConfig.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<ConcertSettlement> listConfirmedSettlements() {
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.ON_CHAIN_STATUS.eq(CONFIRMED))
        .orderBy(CONCERT_SETTLEMENT.ID)
        .fetchInto(ConcertSettlement.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<PendingClaim> listConfirmedClaims() {
    return dsl.selectFrom(PENDING_CLAIM)
        .where(PENDING_CLAIM.ON_CHAIN_STATUS.eq(CONFIRMED))
        .orderBy(PENDING_CLAIM.ID)
        .fetchInto(PendingClaim.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<AcademySubscription> listConfirmedAcademySubscriptions() {
    return dsl.selectFrom(ACADEMY_SUBSCRIPTION)
        .where(ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS.eq(CONFIRMED))
        .orderBy(ACADEMY_SUBSCRIPTION.ID)
        .fetchInto(AcademySubscription.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<GeographicSubscription> listConfirmedGeoSubscriptions() {
    return dsl.selectFrom(GEOGRAPHIC_SUBSCRIPTION)
        .where(GEOGRAPHIC_SUBSCRIPTION.ON_CHAIN_STATUS.eq(CONFIRMED))
        .orderBy(GEOGRAPHIC_SUBSCRIPTION.ID)
        .fetchInto(GeographicSubscription.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<RoyaltyDeposit> listConfirmedRoyaltyDeposits() {
    return dsl.selectFrom(ROYALTY_DEPOSIT)
        .where(ROYALTY_DEPOSIT.ON_CHAIN_STATUS.eq(CONFIRMED))
        .orderBy(ROYALTY_DEPOSIT.ID)
        .fetchInto(RoyaltyDeposit.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<SyncLicenseDeal> listPaidSyncLicenseDeals() {
    return dsl.selectFrom(SYNC_LICENSE_DEAL)
        .where(SYNC_LICENSE_DEAL.STATUS.eq(PAID))
        .orderBy(SYNC_LICENSE_DEAL.ID)
        .fetchInto(SyncLicenseDeal.class);
  }

  /** {@inheritDoc} */
  @Override
  public boolean demoteSettlement(Long id) {
    return dsl.update(CONCERT_SETTLEMENT)
            .set(CONCERT_SETTLEMENT.ON_CHAIN_STATUS, ORPHANED)
            .where(CONCERT_SETTLEMENT.ID.eq(id))
            .and(CONCERT_SETTLEMENT.ON_CHAIN_STATUS.eq(CONFIRMED))
            .execute()
        > 0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean demoteClaim(Long id) {
    return dsl.update(PENDING_CLAIM)
            .set(PENDING_CLAIM.ON_CHAIN_STATUS, ORPHANED)
            .where(PENDING_CLAIM.ID.eq(id))
            .and(PENDING_CLAIM.ON_CHAIN_STATUS.eq(CONFIRMED))
            .execute()
        > 0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean demoteAcademySubscription(Long id) {
    return dsl.update(ACADEMY_SUBSCRIPTION)
            .set(ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS, ORPHANED)
            .where(ACADEMY_SUBSCRIPTION.ID.eq(id))
            .and(ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS.eq(CONFIRMED))
            .execute()
        > 0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean demoteGeoSubscription(Long id) {
    return dsl.update(GEOGRAPHIC_SUBSCRIPTION)
            .set(GEOGRAPHIC_SUBSCRIPTION.ON_CHAIN_STATUS, ORPHANED)
            .where(GEOGRAPHIC_SUBSCRIPTION.ID.eq(id))
            .and(GEOGRAPHIC_SUBSCRIPTION.ON_CHAIN_STATUS.eq(CONFIRMED))
            .execute()
        > 0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean demoteRoyaltyDeposit(Long id) {
    return dsl.update(ROYALTY_DEPOSIT)
            .set(ROYALTY_DEPOSIT.ON_CHAIN_STATUS, ORPHANED)
            .where(ROYALTY_DEPOSIT.ID.eq(id))
            .and(ROYALTY_DEPOSIT.ON_CHAIN_STATUS.eq(CONFIRMED))
            .execute()
        > 0;
  }

  /** {@inheritDoc} */
  @Override
  public boolean demoteSyncLicenseDeal(Long id) {
    return dsl.update(SYNC_LICENSE_DEAL)
            .set(SYNC_LICENSE_DEAL.STATUS, ORPHANED)
            .where(SYNC_LICENSE_DEAL.ID.eq(id))
            .and(SYNC_LICENSE_DEAL.STATUS.eq(PAID))
            .execute()
        > 0;
  }
}
