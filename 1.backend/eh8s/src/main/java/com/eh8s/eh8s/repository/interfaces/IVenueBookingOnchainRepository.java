package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ persistence for on-chain venue listings and VenueAccessToken booking escrow.
 */
public interface IVenueBookingOnchainRepository {

  /**
   * Loads one venue.
   *
   * @param venueId venue id
   * @return venue when present
   */
  Optional<Venue> findVenue(Long venueId);

  /**
   * Venues whose on-chain listing has the given status ({@code pending} / {@code approved}).
   *
   * @param listingStatus listing status
   * @return venues ordered by id
   */
  List<Venue> findVenuesByListingStatus(String listingStatus);

  /**
   * Stores a verified {@code register_venue}: wallet, listing PDA, status {@code pending}.
   *
   * @param venueId venue id
   * @param venueWallet venue wallet that signed
   * @param listingPda VenueListing PDA
   * @param txSignature verified signature
   * @return updated venue
   */
  Venue markRegistered(Long venueId, String venueWallet, String listingPda, String txSignature);

  /**
   * Stores a verified {@code approve_venue}: status {@code approved} and the approving wallet.
   *
   * @param venueId venue id
   * @param approverWallet owner or STAGE agent that signed
   * @param txSignature verified signature
   * @return updated venue
   */
  Venue markApproved(Long venueId, String approverWallet, String txSignature);

  /**
   * Loads one booking.
   *
   * @param bookingId booking id
   * @return booking when present
   */
  Optional<Booking> findBooking(Long bookingId);

  /**
   * Bookings of one venue, newest show first.
   *
   * @param venueId venue id
   * @return bookings
   */
  List<Booking> findBookingsByVenue(Long venueId);

  /**
   * Bookings whose escrow step matches one of the statuses.
   *
   * @param onchainStatuses {@code proposed} / {@code confirmed} / …
   * @return bookings ordered by show date
   */
  List<Booking> findBookingsByOnchainStatus(List<String> onchainStatuses);

  /**
   * Concert row of a booking.
   *
   * @param bookingId booking id
   * @return concert when present
   */
  Optional<Concert> findConcertByBooking(Long bookingId);

  /**
   * Newest SPP cycle of a band (any status).
   *
   * @param bandId band id
   * @return cycle id when present
   */
  Optional<Long> findLatestCycleId(Long bandId);

  /**
   * Creates the concert of a booking (status {@code scheduled}) and pins its id as the
   * booking's on-chain concert id, in one transaction.
   *
   * @param booking booking
   * @param sppCycleId SPP cycle id
   * @return created concert
   */
  Concert createConcertForBooking(Booking booking, Long sppCycleId);

  /**
   * Stores a verified {@code propose_booking}.
   *
   * @param bookingId booking id
   * @param concertId on-chain concert id used in the instruction
   * @param grossUsdc escrowed gross
   * @param accessPda VenueAccessToken PDA
   * @param escrowPda escrow token account PDA
   * @param txSignature verified signature
   * @return updated booking
   */
  Booking markProposed(
      Long bookingId,
      Long concertId,
      BigDecimal grossUsdc,
      String accessPda,
      String escrowPda,
      String txSignature);

  /**
   * Stores a verified {@code confirm_booking} with the contract text and its SHA-256.
   *
   * @param bookingId booking id
   * @param contractText canonical contract text
   * @param contractHashHex SHA-256 hex
   * @param txSignature verified signature
   * @return updated booking
   */
  Booking markConfirmed(
      Long bookingId, String contractText, String contractHashHex, String txSignature);

  /**
   * Stores a verified {@code cancel_booking}; clears the closed PDAs so the slot can be proposed
   * again.
   *
   * @param bookingId booking id
   * @param txSignature verified signature
   * @return updated booking
   */
  Booking markCancelled(Long bookingId, String txSignature);

  /**
   * Sum of the concert expense lines.
   *
   * @param concertId concert id
   * @return total (0 when none)
   */
  BigDecimal sumExpenses(Long concertId);

  /**
   * Settlement row of a concert.
   *
   * @param concertId concert id
   * @return settlement when present
   */
  Optional<ConcertSettlement> findSettlementByConcert(Long concertId);

  /**
   * Stores a verified {@code settle_booking} on the settlement, its concert and the booking.
   *
   * @param settlementId settlement id
   * @param bookingId booking id
   * @param signerWallet owner or VAULT agent that signed
   * @param txSignature verified signature
   * @param concertSettlementPda ConcertSettlement PDA
   * @return updated settlement
   */
  ConcertSettlement markSettledFromEscrow(
      Long settlementId,
      Long bookingId,
      String signerWallet,
      String txSignature,
      String concertSettlementPda);

  /**
   * True when any venue / booking / settlement column already stores this signature.
   *
   * @param txSignature base58 signature
   * @return whether it is recorded
   */
  boolean isRecorded(String txSignature);
}
