package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ reads and writes behind Stage Map pins, venue open dates, and slot requests.
 */
public interface IStageMapRepository {

  /**
   * Lists every venue.
   *
   * @return venues ordered by id
   */
  List<Venue> findVenues();

  /**
   * Loads one venue.
   *
   * @param venueId venue id
   * @return venue when it exists
   */
  Optional<Venue> findVenue(Long venueId);

  /**
   * Lists contract types (fixed guarantee, door, versus, promoter).
   *
   * @return contract types ordered by id
   */
  List<ContractType> findContractTypes();

  /**
   * Lists open dates of every venue in [from, toExclusive).
   *
   * @param from inclusive start
   * @param toExclusive exclusive end
   * @return availability rows ordered by date
   */
  List<VenueAvailability> findAvailabilityBetween(LocalDate from, LocalDate toExclusive);

  /**
   * Checks whether a venue already published an open date.
   *
   * @param venueId venue id
   * @param date calendar date
   * @return true when the row exists
   */
  boolean availabilityExists(Long venueId, LocalDate date);

  /**
   * Inserts one open date.
   *
   * @param availability row (id ignored)
   * @return stored row with id
   */
  VenueAvailability insertAvailability(VenueAvailability availability);

  /**
   * Lists bookings of every venue with a show date in [from, toExclusive).
   *
   * @param from inclusive start
   * @param toExclusive exclusive end
   * @return bookings ordered by show date
   */
  List<Booking> findBookingsBetween(LocalDate from, LocalDate toExclusive);

  /**
   * Reads the wallet linked to an account.
   *
   * @param accountId account id from the JWT
   * @return wallet pubkey when the account has one
   */
  Optional<String> findAccountWallet(Long accountId);

  /**
   * Resolves the musician profile that belongs to an account.
   *
   * @param accountId account id from the JWT
   * @return profile id when the account is a musician
   */
  Optional<Long> findMusicianProfileId(Long accountId);

  /**
   * Lists the bands a musician plays in.
   *
   * @param musicianProfileId musician profile id
   * @return band ids ordered by id
   */
  List<Long> findBandIds(Long musicianProfileId);

  /**
   * Checks whether a band already holds a booking row for that venue and date.
   *
   * @param venueId venue id
   * @param bandId band id
   * @param showDate show date
   * @return true when the row exists (unique slot)
   */
  boolean bookingExists(Long venueId, Long bandId, LocalDate showDate);

  /**
   * Inserts one booking row.
   *
   * @param booking booking (id ignored)
   * @return stored row with id
   */
  Booking insertBooking(Booking booking);
}
