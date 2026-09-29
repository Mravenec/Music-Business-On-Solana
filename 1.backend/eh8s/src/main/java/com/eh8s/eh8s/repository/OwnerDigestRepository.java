package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.AGENT_EVENT_LOG;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.OWNER_DIGEST;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.records.OwnerDigestRecord;
import com.eh8s.eh8s.repository.interfaces.IOwnerDigestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * JOOQ persistence for the Claude owner digest.
 */
@Repository
public class OwnerDigestRepository implements IOwnerDigestRepository {

  private static final String CONFIRMED = "confirmed";

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public OwnerDigestRepository(DSLContext dsl) {
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
  public int countAcademySubscriptions() {
    return dsl.fetchCount(ACADEMY_SUBSCRIPTION);
  }

  /** {@inheritDoc} */
  @Override
  public int countAcademyPaidSince(LocalDateTime since) {
    return dsl.fetchCount(ACADEMY_SUBSCRIPTION, ACADEMY_SUBSCRIPTION.PAID_AT.ge(since));
  }

  /** {@inheritDoc} */
  @Override
  public int countAcademyConfirmed() {
    return dsl.fetchCount(ACADEMY_SUBSCRIPTION, ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS.eq(CONFIRMED));
  }

  /** {@inheritDoc} */
  @Override
  public int countSettlements() {
    return dsl.fetchCount(CONCERT_SETTLEMENT);
  }

  /** {@inheritDoc} */
  @Override
  public int countSettlementsConfirmed() {
    return dsl.fetchCount(CONCERT_SETTLEMENT, CONCERT_SETTLEMENT.ON_CHAIN_STATUS.eq(CONFIRMED));
  }

  /** {@inheritDoc} */
  @Override
  public List<String> findClaimStatuses() {
    return dsl.selectDistinct(PENDING_CLAIM.STATUS)
        .from(PENDING_CLAIM)
        .orderBy(PENDING_CLAIM.STATUS)
        .fetch(PENDING_CLAIM.STATUS);
  }

  /** {@inheritDoc} */
  @Override
  public int countClaims(String status) {
    return dsl.fetchCount(PENDING_CLAIM, PENDING_CLAIM.STATUS.eq(status));
  }

  /** {@inheritDoc} */
  @Override
  public int countActiveBandVaults() {
    return dsl.fetchCount(BAND, BAND.BAND_VAULT_PDA.isNotNull());
  }

  /** {@inheritDoc} */
  @Override
  public int countAgentEventsSince(LocalDateTime since) {
    return dsl.fetchCount(AGENT_EVENT_LOG, AGENT_EVENT_LOG.CREATED_AT.ge(since));
  }

  /** {@inheritDoc} */
  @Override
  public OwnerDigest insert(OwnerDigest digest) {
    OwnerDigestRecord rec = dsl.newRecord(OWNER_DIGEST, digest);
    rec.changed(OWNER_DIGEST.ID, false);
    rec.store();
    return rec.into(OwnerDigest.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<OwnerDigest> latest() {
    return dsl.selectFrom(OWNER_DIGEST)
        .orderBy(OWNER_DIGEST.ID.desc())
        .limit(1)
        .fetchOptionalInto(OwnerDigest.class);
  }
}
