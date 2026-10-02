package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.INSTRUCTOR_PROFILE;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * JOOQ reads for studio credits. Payments the wallet sent are not included.
 */
@Repository
public class StudioLedgerRepository implements IStudioLedgerRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public StudioLedgerRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Account> findAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOptionalInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> confirmedClaims(
      String walletPubkey, LocalDateTime start, LocalDateTime end) {
    return dsl.selectFrom(PENDING_CLAIM)
        .where(PENDING_CLAIM.CLAIMER_WALLET_PUBKEY.eq(walletPubkey))
        .and(PENDING_CLAIM.ON_CHAIN_STATUS.equalIgnoreCase("confirmed"))
        .and(PENDING_CLAIM.CLAIMED_AT.ge(start))
        .and(PENDING_CLAIM.CLAIMED_AT.lt(end))
        .orderBy(PENDING_CLAIM.CLAIMED_AT.asc())
        .fetchInto(PendingClaim.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademySubscription> instructorShares(
      String walletPubkey, LocalDateTime start, LocalDateTime end) {
    return dsl.select(ACADEMY_SUBSCRIPTION.fields())
        .from(ACADEMY_SUBSCRIPTION)
        .join(INSTRUCTOR_PROFILE)
        .on(INSTRUCTOR_PROFILE.ID.eq(ACADEMY_SUBSCRIPTION.INSTRUCTOR_PROFILE_ID))
        .join(ACCOUNT)
        .on(ACCOUNT.ID.eq(INSTRUCTOR_PROFILE.ACCOUNT_ID))
        .where(ACCOUNT.WALLET_PUBKEY.eq(walletPubkey))
        .and(ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS.equalIgnoreCase("confirmed"))
        .and(ACADEMY_SUBSCRIPTION.INSTRUCTOR_USDC.gt(BigDecimal.ZERO))
        .and(ACADEMY_SUBSCRIPTION.PAID_AT.ge(start))
        .and(ACADEMY_SUBSCRIPTION.PAID_AT.lt(end))
        .orderBy(ACADEMY_SUBSCRIPTION.PAID_AT.asc())
        .fetchInto(AcademySubscription.class);
  }
}
