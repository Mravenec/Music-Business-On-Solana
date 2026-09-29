package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertExpense;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.BookingRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.ConcertRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.ConcertSettlementRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.PendingClaimRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.VenueRecord;
import com.eh8s.eh8s.repository.interfaces.IVenueRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BOOKING;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_EXPENSE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONTRACT_TYPE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_MEMBER_SCORE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;

/**
 * JOOQ persistence for venue and settlement tables.
 */
@Repository
public class VenueRepository implements IVenueRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public VenueRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Venue> findVenues() {
    return dsl.selectFrom(VENUE).orderBy(VENUE.ID).fetchInto(Venue.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Venue insertVenue(Venue venue) {
    VenueRecord rec = dsl.newRecord(VENUE, venue);
    rec.changed(VENUE.ID, false);
    rec.store();
    return rec.into(Venue.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ContractType> findContractTypes() {
    return dsl.selectFrom(CONTRACT_TYPE).orderBy(CONTRACT_TYPE.ID).fetchInto(ContractType.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Booking> findBookings() {
    return dsl.selectFrom(BOOKING).orderBy(BOOKING.ID).fetchInto(Booking.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Booking insertBooking(Booking booking) {
    BookingRecord rec = dsl.newRecord(BOOKING, booking);
    rec.changed(BOOKING.ID, false);
    rec.store();
    return rec.into(Booking.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Concert> findConcerts() {
    return dsl.selectFrom(CONCERT).orderBy(CONCERT.ID).fetchInto(Concert.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Concert insertConcert(Concert concert) {
    ConcertRecord rec = dsl.newRecord(CONCERT, concert);
    rec.changed(CONCERT.ID, false);
    rec.store();
    return rec.into(Concert.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<PendingClaim> updateClaimStatus(Long claimId, String status) {
    int updated =
        dsl.update(PENDING_CLAIM)
            .set(PENDING_CLAIM.STATUS, status)
            .where(PENDING_CLAIM.ID.eq(claimId))
            .execute();
    if (updated == 0) {
      return Optional.empty();
    }
    return dsl.selectFrom(PENDING_CLAIM)
        .where(PENDING_CLAIM.ID.eq(claimId))
        .fetchOptionalInto(PendingClaim.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Concert> findConcert(Long concertId) {
    return dsl.selectFrom(CONCERT).where(CONCERT.ID.eq(concertId)).fetchOptionalInto(Concert.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertExpense> findExpenses(Long concertId) {
    return dsl.selectFrom(CONCERT_EXPENSE)
        .where(CONCERT_EXPENSE.CONCERT_ID.eq(concertId))
        .fetchInto(ConcertExpense.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppMemberScore> findScores(Long cycleId) {
    if (cycleId == null) {
      return List.of();
    }
    return dsl.selectFrom(SPP_MEMBER_SCORE)
        .where(SPP_MEMBER_SCORE.SPP_CYCLE_ID.eq(cycleId))
        .orderBy(SPP_MEMBER_SCORE.ID)
        .fetchInto(SppMemberScore.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> findBandMemberMusicianIds(Long bandId) {
    if (bandId == null) {
      return List.of();
    }
    return dsl.select(BAND_MEMBER.MUSICIAN_PROFILE_ID)
        .from(BAND_MEMBER)
        .where(BAND_MEMBER.BAND_ID.eq(bandId))
        .orderBy(BAND_MEMBER.ID)
        .fetch(BAND_MEMBER.MUSICIAN_PROFILE_ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<String> findWalletByMusicianProfileId(Long musicianProfileId) {
    if (musicianProfileId == null) {
      return Optional.empty();
    }
    return dsl.select(ACCOUNT.WALLET_PUBKEY)
        .from(MUSICIAN_PROFILE)
        .join(ACCOUNT)
        .on(ACCOUNT.ID.eq(MUSICIAN_PROFILE.ACCOUNT_ID))
        .where(MUSICIAN_PROFILE.ID.eq(musicianProfileId))
        .fetchOptional(ACCOUNT.WALLET_PUBKEY);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<ConcertSettlement> findSettlementByConcert(Long concertId) {
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.CONCERT_ID.eq(concertId))
        .fetchOptionalInto(ConcertSettlement.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConcertSettlement insertSettlement(ConcertSettlement settlement) {
    ConcertSettlementRecord rec = dsl.newRecord(CONCERT_SETTLEMENT, settlement);
    rec.changed(CONCERT_SETTLEMENT.ID, false);
    rec.store();
    return rec.into(ConcertSettlement.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void insertClaims(List<PendingClaim> claims) {
    for (PendingClaim claim : claims) {
      PendingClaimRecord rec = dsl.newRecord(PENDING_CLAIM, claim);
      rec.changed(PENDING_CLAIM.ID, false);
      rec.store();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void markConcertSettled(Long concertId) {
    dsl.update(CONCERT).set(CONCERT.STATUS, "settled").where(CONCERT.ID.eq(concertId)).execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<PendingClaim> findClaims(Long settlementId) {
    return dsl.selectFrom(PENDING_CLAIM)
        .where(PENDING_CLAIM.CONCERT_SETTLEMENT_ID.eq(settlementId))
        .orderBy(PENDING_CLAIM.ID)
        .fetchInto(PendingClaim.class);
  }
}
