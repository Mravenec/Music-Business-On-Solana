package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertExpense;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for venues, bookings, concerts, and settlements.
 */
public interface IVenueRepository {

  /**
   * @return every venue
   */
  List<Venue> findVenues();

  /**
   * Inserts a venue.
   *
   * @param venue row
   * @return stored venue
   */
  Venue insertVenue(Venue venue);

  /**
   * @return contract types
   */
  List<ContractType> findContractTypes();

  /**
   * @return bookings
   */
  List<Booking> findBookings();

  /**
   * Inserts a booking.
   *
   * @param booking row
   * @return stored booking
   */
  Booking insertBooking(Booking booking);

  /**
   * @return concerts
   */
  List<Concert> findConcerts();

  /**
   * Inserts a concert.
   *
   * @param concert row
   * @return stored concert
   */
  Concert insertConcert(Concert concert);

  /**
   * Updates a claim status.
   *
   * @param claimId claim primary key
   * @param status new status
   * @return updated claim
   */
  Optional<PendingClaim> updateClaimStatus(Long claimId, String status);
  /**
   * @param concertId concert primary key
   * @return the concert, or empty
   */
  Optional<Concert> findConcert(Long concertId);

  /**
   * @param concertId concert primary key
   * @return expense lines
   */
  List<ConcertExpense> findExpenses(Long concertId);

  /**
   * @param cycleId SPP cycle
   * @return scores used to split the pool
   */
  List<SppMemberScore> findScores(Long cycleId);

  /**
   * Musician profile ids for active band members (no-mock settle fallback).
   *
   * @param bandId band primary key
   * @return musician profile ids in member order
   */
  List<Long> findBandMemberMusicianIds(Long bandId);

  /**
   * Wallet pubkey for a musician profile (account join).
   *
   * @param musicianProfileId profile id
   * @return base58 wallet when present
   */
  Optional<String> findWalletByMusicianProfileId(Long musicianProfileId);

  /**
   * @param concertId concert primary key
   * @return existing settlement if any
   */
  Optional<ConcertSettlement> findSettlementByConcert(Long concertId);

  /**
   * @param settlement settlement to insert
   * @return stored row
   */
  ConcertSettlement insertSettlement(ConcertSettlement settlement);

  /**
   * @param claims pending claims to insert
   */
  void insertClaims(List<PendingClaim> claims);

  /**
   * Marks the concert as settled.
   *
   * @param concertId concert primary key
   */
  void markConcertSettled(Long concertId);

  /**
   * @param settlementId settlement primary key
   * @return claims
   */
  List<PendingClaim> findClaims(Long settlementId);
}
