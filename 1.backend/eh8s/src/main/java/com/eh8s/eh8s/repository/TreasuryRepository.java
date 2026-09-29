package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEOGRAPHIC_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEO_TIER;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.GOVERNANCE;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.TREASURY_WITHDRAWAL;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.TreasuryWithdrawalRecord;
import com.eh8s.eh8s.repository.interfaces.ITreasuryRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * JOOQ persistence for the owner treasury screen.
 */
@Repository
public class TreasuryRepository implements ITreasuryRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public TreasuryRepository(DSLContext dsl) {
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
  public List<AcademySubscription> findPaidAcademySubscriptions(int limit) {
    return dsl.selectFrom(ACADEMY_SUBSCRIPTION)
        .where(ACADEMY_SUBSCRIPTION.PAY_TX_SIGNATURE.isNotNull())
        .orderBy(ACADEMY_SUBSCRIPTION.PAID_AT.desc())
        .limit(limit)
        .fetchInto(AcademySubscription.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<GeographicSubscription> findPaidGeographicSubscriptions(int limit) {
    return dsl.selectFrom(GEOGRAPHIC_SUBSCRIPTION)
        .where(GEOGRAPHIC_SUBSCRIPTION.PAY_TX_SIGNATURE.isNotNull())
        .orderBy(GEOGRAPHIC_SUBSCRIPTION.PAID_AT.desc())
        .limit(limit)
        .fetchInto(GeographicSubscription.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<GeoTier> findGeoTiersByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(GEO_TIER).where(GEO_TIER.ID.in(ids)).fetchInto(GeoTier.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<ConcertSettlement> findSettledConcerts(int limit) {
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.SETTLE_TX_SIGNATURE.isNotNull())
        .orderBy(CONCERT_SETTLEMENT.SETTLED_AT.desc())
        .limit(limit)
        .fetchInto(ConcertSettlement.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<TreasuryWithdrawal> listWithdrawals() {
    return dsl.selectFrom(TREASURY_WITHDRAWAL)
        .orderBy(TREASURY_WITHDRAWAL.ID.desc())
        .fetchInto(TreasuryWithdrawal.class);
  }

  /** {@inheritDoc} */
  @Override
  public TreasuryWithdrawal insertWithdrawal(TreasuryWithdrawal withdrawal) {
    TreasuryWithdrawalRecord rec = dsl.newRecord(TREASURY_WITHDRAWAL, withdrawal);
    rec.changed(TREASURY_WITHDRAWAL.ID, false);
    rec.store();
    return rec.into(TreasuryWithdrawal.class);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecorded(String txSignature) {
    return dsl.fetchExists(TREASURY_WITHDRAWAL, TREASURY_WITHDRAWAL.TX_SIGNATURE.eq(txSignature));
  }

  /** {@inheritDoc} */
  @Override
  public boolean governanceActive() {
    return dsl.fetchExists(GOVERNANCE);
  }
}
