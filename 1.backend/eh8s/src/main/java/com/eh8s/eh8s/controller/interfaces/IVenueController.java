package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.util.List;

/**
 * HTTP contract for stage map and settlement resources.
 */
public interface IVenueController {

  /**
   * @return venues
   */
  List<Venue> venues();

  /**
   * Creates a venue.
   *
   * @param venue body
   * @return stored venue
   */
  Venue createVenue(Venue venue);

  /**
   * @return contract types
   */
  List<ContractType> contractTypes();

  /**
   * @return bookings
   */
  List<Booking> bookings();

  /**
   * Creates a booking.
   *
   * @param booking body
   * @return stored booking
   */
  Booking createBooking(Booking booking);

  /**
   * @return concerts
   */
  List<Concert> concerts();

  /**
   * Creates a concert.
   *
   * @param concert body
   * @return stored concert
   */
  Concert createConcert(Concert concert);

  /**
   * @param concertId concert to settle
   * @param input settlement POJO carrying grossUsdc (optional; defaults to 875.00)
   * @return settlement
   */
  ConcertSettlement settle(Long concertId, ConcertSettlement input);

  /**
   * @param settlementId settlement primary key
   * @return claims
   */
  List<PendingClaim> claims(Long settlementId);

  /**
   * Marks a claim as claimed.
   *
   * @param claimId claim primary key
   * @return updated claim
   */
  PendingClaim markClaim(Long claimId);
}
