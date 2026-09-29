package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.BookingRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.VenueAvailabilityRecord;
import com.eh8s.eh8s.repository.interfaces.IStageMapRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BOOKING;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONTRACT_TYPE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE_AVAILABILITY;

/**
 * JOOQ persistence for Stage Map pins, venue availability, and slot requests.
 */
@Repository
public class StageMapRepository implements IStageMapRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public StageMapRepository(DSLContext dsl) {
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
  public Optional<Venue> findVenue(Long venueId) {
    return dsl.selectFrom(VENUE).where(VENUE.ID.eq(venueId)).fetchOptionalInto(Venue.class);
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
  public List<VenueAvailability> findAvailabilityBetween(LocalDate from, LocalDate toExclusive) {
    return dsl.selectFrom(VENUE_AVAILABILITY)
        .where(VENUE_AVAILABILITY.AVAILABLE_DATE.ge(from))
        .and(VENUE_AVAILABILITY.AVAILABLE_DATE.lt(toExclusive))
        .orderBy(VENUE_AVAILABILITY.AVAILABLE_DATE, VENUE_AVAILABILITY.ID)
        .fetchInto(VenueAvailability.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean availabilityExists(Long venueId, LocalDate date) {
    return dsl.fetchExists(
        VENUE_AVAILABILITY,
        VENUE_AVAILABILITY.VENUE_ID.eq(venueId).and(VENUE_AVAILABILITY.AVAILABLE_DATE.eq(date)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public VenueAvailability insertAvailability(VenueAvailability availability) {
    VenueAvailabilityRecord rec = dsl.newRecord(VENUE_AVAILABILITY, availability);
    rec.changed(VENUE_AVAILABILITY.ID, false);
    rec.store();
    return rec.into(VenueAvailability.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Booking> findBookingsBetween(LocalDate from, LocalDate toExclusive) {
    return dsl.selectFrom(BOOKING)
        .where(BOOKING.SHOW_DATE.ge(from))
        .and(BOOKING.SHOW_DATE.lt(toExclusive))
        .orderBy(BOOKING.SHOW_DATE, BOOKING.ID)
        .fetchInto(Booking.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<String> findAccountWallet(Long accountId) {
    return dsl.select(ACCOUNT.WALLET_PUBKEY)
        .from(ACCOUNT)
        .where(ACCOUNT.ID.eq(accountId))
        .fetchOptional(ACCOUNT.WALLET_PUBKEY);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Long> findMusicianProfileId(Long accountId) {
    return dsl.select(MUSICIAN_PROFILE.ID)
        .from(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .fetchOptional(MUSICIAN_PROFILE.ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Long> findBandIds(Long musicianProfileId) {
    return dsl.select(BAND_MEMBER.BAND_ID)
        .from(BAND_MEMBER)
        .where(BAND_MEMBER.MUSICIAN_PROFILE_ID.eq(musicianProfileId))
        .orderBy(BAND_MEMBER.BAND_ID)
        .fetch(BAND_MEMBER.BAND_ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean bookingExists(Long venueId, Long bandId, LocalDate showDate) {
    return dsl.fetchExists(
        BOOKING,
        BOOKING
            .VENUE_ID
            .eq(venueId)
            .and(BOOKING.BAND_ID.eq(bandId))
            .and(BOOKING.SHOW_DATE.eq(showDate)));
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
}
