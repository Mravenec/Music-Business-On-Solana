package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.STUDIO_PARTNER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.STUDIO_PARTNER_ALLOCATION;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.INSTRUCTOR_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.SYNC_LICENSE_DEAL;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.repository.interfaces.IStudioLedgerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * JOOQ reads and partner-book writes. Payments a wallet sent are not credits.
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
  public List<PendingClaim> soloClaims(String walletPubkey, LocalDateTime start, LocalDateTime end) {
    return claims(walletPubkey, start, end, memberCount().eq(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> bandClaims(String walletPubkey, LocalDateTime start, LocalDateTime end) {
    return claims(walletPubkey, start, end, memberCount().ne(1));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSettlement> venueExpenses(
      String walletPubkey, LocalDateTime start, LocalDateTime end) {
    return dsl.select(CONCERT_SETTLEMENT.fields())
        .from(CONCERT_SETTLEMENT)
        .join(CONCERT)
        .on(CONCERT.ID.eq(CONCERT_SETTLEMENT.CONCERT_ID))
        .join(VENUE)
        .on(VENUE.ID.eq(CONCERT.VENUE_ID))
        .where(VENUE.WALLET_PUBKEY.eq(walletPubkey))
        .and(CONCERT_SETTLEMENT.ON_CHAIN_STATUS.equalIgnoreCase("confirmed"))
        .and(CONCERT_SETTLEMENT.EXPENSES_USDC.gt(BigDecimal.ZERO))
        .and(CONCERT_SETTLEMENT.SETTLED_AT.ge(start))
        .and(CONCERT_SETTLEMENT.SETTLED_AT.lt(end))
        .orderBy(CONCERT_SETTLEMENT.SETTLED_AT.asc())
        .fetchInto(ConcertSettlement.class);
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

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartner> activePartners() {
    return dsl.selectFrom(STUDIO_PARTNER)
        .where(STUDIO_PARTNER.ACTIVE.eq((byte) 1))
        .orderBy(STUDIO_PARTNER.DISPLAY_NAME.asc())
        .fetchInto(StudioPartner.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartner> allPartners() {
    return dsl.selectFrom(STUDIO_PARTNER)
        .orderBy(STUDIO_PARTNER.ID.asc())
        .fetchInto(StudioPartner.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<StudioPartner> findPartnerByWallet(String walletPubkey) {
    return dsl.selectFrom(STUDIO_PARTNER)
        .where(STUDIO_PARTNER.WALLET_PUBKEY.eq(walletPubkey))
        .fetchOptionalInto(StudioPartner.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<StudioPartner> findPartner(Long partnerId) {
    return dsl.selectFrom(STUDIO_PARTNER)
        .where(STUDIO_PARTNER.ID.eq(partnerId))
        .fetchOptionalInto(StudioPartner.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int activeShareBps(Long exceptPartnerId) {
    Condition skip =
        exceptPartnerId == null ? DSL.noCondition() : STUDIO_PARTNER.ID.ne(exceptPartnerId);
    Integer sum =
        dsl.select(DSL.coalesce(DSL.sum(STUDIO_PARTNER.SHARE_BPS), 0))
            .from(STUDIO_PARTNER)
            .where(STUDIO_PARTNER.ACTIVE.eq((byte) 1))
            .and(skip)
            .fetchOne(0, Integer.class);
    return sum == null ? 0 : sum;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner insertPartner(String walletPubkey, String displayName, int shareBps) {
    dsl.insertInto(STUDIO_PARTNER)
        .set(STUDIO_PARTNER.WALLET_PUBKEY, walletPubkey)
        .set(STUDIO_PARTNER.DISPLAY_NAME, displayName)
        .set(STUDIO_PARTNER.SHARE_BPS, shareBps)
        .set(STUDIO_PARTNER.ACTIVE, (byte) 1)
        .set(STUDIO_PARTNER.STARTED_AT, LocalDateTime.now())
        .execute();
    return findPartnerByWallet(walletPubkey).orElseThrow();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner updatePartner(Long partnerId, String displayName, int shareBps) {
    StudioPartner current = findPartner(partnerId).orElseThrow();
    boolean rejoining = current.getActive() == null || current.getActive() != (byte) 1;
    if (rejoining) {
      dsl.update(STUDIO_PARTNER)
          .set(STUDIO_PARTNER.DISPLAY_NAME, displayName)
          .set(STUDIO_PARTNER.SHARE_BPS, shareBps)
          .set(STUDIO_PARTNER.ACTIVE, (byte) 1)
          .set(STUDIO_PARTNER.STARTED_AT, LocalDateTime.now())
          .set(STUDIO_PARTNER.ENDED_AT, (LocalDateTime) null)
          .where(STUDIO_PARTNER.ID.eq(partnerId))
          .execute();
    } else {
      dsl.update(STUDIO_PARTNER)
          .set(STUDIO_PARTNER.DISPLAY_NAME, displayName)
          .set(STUDIO_PARTNER.SHARE_BPS, shareBps)
          .set(STUDIO_PARTNER.ACTIVE, (byte) 1)
          .where(STUDIO_PARTNER.ID.eq(partnerId))
          .execute();
    }
    return findPartner(partnerId).orElseThrow();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StudioPartner deactivatePartner(Long partnerId) {
    StudioPartner current = findPartner(partnerId).orElseThrow();
    if (current.getEndedAt() == null) {
      dsl.update(STUDIO_PARTNER)
          .set(STUDIO_PARTNER.ACTIVE, (byte) 0)
          .set(STUDIO_PARTNER.ENDED_AT, LocalDateTime.now())
          .where(STUDIO_PARTNER.ID.eq(partnerId))
          .execute();
    } else {
      dsl.update(STUDIO_PARTNER)
          .set(STUDIO_PARTNER.ACTIVE, (byte) 0)
          .where(STUDIO_PARTNER.ID.eq(partnerId))
          .execute();
    }
    return findPartner(partnerId).orElseThrow();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSettlement> showFees(LocalDateTime start, LocalDateTime end) {
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.ON_CHAIN_STATUS.equalIgnoreCase("confirmed"))
        .and(CONCERT_SETTLEMENT.EH8S_FEE_USDC.gt(BigDecimal.ZERO))
        .and(CONCERT_SETTLEMENT.SETTLED_AT.ge(start))
        .and(CONCERT_SETTLEMENT.SETTLED_AT.lt(end))
        .orderBy(CONCERT_SETTLEMENT.SETTLED_AT.asc())
        .fetchInto(ConcertSettlement.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademySubscription> academyFees(LocalDateTime start, LocalDateTime end) {
    return dsl.selectFrom(ACADEMY_SUBSCRIPTION)
        .where(ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS.equalIgnoreCase("confirmed"))
        .and(ACADEMY_SUBSCRIPTION.TREASURY_USDC.gt(BigDecimal.ZERO))
        .and(ACADEMY_SUBSCRIPTION.PAID_AT.ge(start))
        .and(ACADEMY_SUBSCRIPTION.PAID_AT.lt(end))
        .orderBy(ACADEMY_SUBSCRIPTION.PAID_AT.asc())
        .fetchInto(AcademySubscription.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SyncLicenseDeal> syncFees(LocalDateTime start, LocalDateTime end) {
    return dsl.selectFrom(SYNC_LICENSE_DEAL)
        .where(SYNC_LICENSE_DEAL.STATUS.equalIgnoreCase("paid"))
        .and(SYNC_LICENSE_DEAL.EH8S_USDC.gt(BigDecimal.ZERO))
        .and(SYNC_LICENSE_DEAL.PAID_AT.ge(start))
        .and(SYNC_LICENSE_DEAL.PAID_AT.lt(end))
        .orderBy(SYNC_LICENSE_DEAL.PAID_AT.asc())
        .fetchInto(SyncLicenseDeal.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clearAllocations(int year, int month) {
    dsl.deleteFrom(STUDIO_PARTNER_ALLOCATION)
        .where(STUDIO_PARTNER_ALLOCATION.YEAR_NUM.eq((short) year))
        .and(STUDIO_PARTNER_ALLOCATION.MONTH_NUM.eq((byte) month))
        .execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void insertAllocation(
      Long partnerId, int year, int month, String sourceCode, BigDecimal amountUsdc) {
    dsl.insertInto(STUDIO_PARTNER_ALLOCATION)
        .set(STUDIO_PARTNER_ALLOCATION.STUDIO_PARTNER_ID, partnerId)
        .set(STUDIO_PARTNER_ALLOCATION.YEAR_NUM, (short) year)
        .set(STUDIO_PARTNER_ALLOCATION.MONTH_NUM, (byte) month)
        .set(STUDIO_PARTNER_ALLOCATION.SOURCE_CODE, sourceCode)
        .set(STUDIO_PARTNER_ALLOCATION.AMOUNT_USDC, amountUsdc)
        .execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartnerAllocation> allocations(int year, int month) {
    return dsl.selectFrom(STUDIO_PARTNER_ALLOCATION)
        .where(STUDIO_PARTNER_ALLOCATION.YEAR_NUM.eq((short) year))
        .and(STUDIO_PARTNER_ALLOCATION.MONTH_NUM.eq((byte) month))
        .orderBy(STUDIO_PARTNER_ALLOCATION.STUDIO_PARTNER_ID.asc())
        .fetchInto(StudioPartnerAllocation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<StudioPartnerAllocation> allocationsForWallet(
      String walletPubkey, int year, int month) {
    return dsl.select(STUDIO_PARTNER_ALLOCATION.fields())
        .from(STUDIO_PARTNER_ALLOCATION)
        .join(STUDIO_PARTNER)
        .on(STUDIO_PARTNER.ID.eq(STUDIO_PARTNER_ALLOCATION.STUDIO_PARTNER_ID))
        .where(STUDIO_PARTNER.WALLET_PUBKEY.eq(walletPubkey))
        .and(STUDIO_PARTNER_ALLOCATION.YEAR_NUM.eq((short) year))
        .and(STUDIO_PARTNER_ALLOCATION.MONTH_NUM.eq((byte) month))
        .orderBy(STUDIO_PARTNER_ALLOCATION.SOURCE_CODE.asc())
        .fetchInto(StudioPartnerAllocation.class);
  }

  private List<PendingClaim> claims(
      String walletPubkey, LocalDateTime start, LocalDateTime end, Condition members) {
    return dsl.select(PENDING_CLAIM.fields())
        .from(PENDING_CLAIM)
        .join(CONCERT_SETTLEMENT)
        .on(CONCERT_SETTLEMENT.ID.eq(PENDING_CLAIM.CONCERT_SETTLEMENT_ID))
        .join(CONCERT)
        .on(CONCERT.ID.eq(CONCERT_SETTLEMENT.CONCERT_ID))
        .join(BAND)
        .on(BAND.ID.eq(CONCERT.BAND_ID))
        .where(PENDING_CLAIM.CLAIMER_WALLET_PUBKEY.eq(walletPubkey))
        .and(PENDING_CLAIM.ON_CHAIN_STATUS.equalIgnoreCase("confirmed"))
        .and(PENDING_CLAIM.CLAIMED_AT.ge(start))
        .and(PENDING_CLAIM.CLAIMED_AT.lt(end))
        .and(members)
        .orderBy(PENDING_CLAIM.CLAIMED_AT.asc())
        .fetchInto(PendingClaim.class);
  }

  private Field<Integer> memberCount() {
    return DSL.selectCount()
        .from(BAND_MEMBER)
        .where(BAND_MEMBER.BAND_ID.eq(BAND.ID))
        .asField();
  }
}
