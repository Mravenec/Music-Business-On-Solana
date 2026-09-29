package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.math.BigDecimal;
import java.util.List;

/**
 * Stage map and off-chain concert settlement use cases.
 */
public interface IVenueService {

  /**
   * @return venues for the stage map
   */
  List<Venue> venues();

  /**
   * Creates a venue pin.
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
   * Creates a concert from a booking.
   *
   * @param concert body
   * @return stored concert
   */
  Concert createConcert(Concert concert);

  /**
   * Settles a concert: gross minus expenses, 15% EH8S fee, SPP pending claims.
   *
   * @param concertId concert primary key
   * @param grossUsdc venue deposit
   * @return stored settlement
   */
  ConcertSettlement settle(Long concertId, BigDecimal grossUsdc);

  /**
   * @param settlementId settlement primary key
   * @return pending claims
   */
  List<PendingClaim> claims(Long settlementId);

  /**
   * Marks a pending claim as claimed (off-chain status).
   *
   * @param claimId claim primary key
   * @return updated claim
   */
  PendingClaim markClaim(Long claimId);
}
