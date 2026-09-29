package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BOOKING;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_EXPENSE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_CYCLE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.ConcertRecord;
import com.eh8s.eh8s.repository.interfaces.IVenueBookingOnchainRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOOQ persistence for on-chain venue listings and VenueAccessToken booking escrow.
 */
@Repository
public class VenueBookingOnchainRepository implements IVenueBookingOnchainRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public VenueBookingOnchainRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Venue> findVenue(Long venueId) {
    return dsl.selectFrom(VENUE).where(VENUE.ID.eq(venueId)).fetchOptionalInto(Venue.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Venue> findVenuesByListingStatus(String listingStatus) {
    return dsl.selectFrom(VENUE)
        .where(VENUE.LISTING_STATUS.eq(listingStatus))
        .orderBy(VENUE.ID)
        .fetchInto(Venue.class);
  }

  /** {@inheritDoc} */
  @Override
  public Venue markRegistered(
      Long venueId, String venueWallet, String listingPda, String txSignature) {
    dsl.update(VENUE)
        .set(VENUE.WALLET_PUBKEY, venueWallet)
        .set(VENUE.VENUE_LISTING_PDA, listingPda)
        .set(VENUE.LISTING_STATUS, "pending")
        .set(VENUE.REGISTER_TX_SIGNATURE, txSignature)
        .where(VENUE.ID.eq(venueId))
        .execute();
    return findVenue(venueId).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public Venue markApproved(Long venueId, String approverWallet, String txSignature) {
    dsl.update(VENUE)
        .set(VENUE.LISTING_STATUS, "approved")
        .set(VENUE.APPROVE_TX_SIGNATURE, txSignature)
        .set(VENUE.APPROVED_BY_WALLET, approverWallet)
        .where(VENUE.ID.eq(venueId))
        .execute();
    return findVenue(venueId).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Booking> findBooking(Long bookingId) {
    return dsl.selectFrom(BOOKING).where(BOOKING.ID.eq(bookingId)).fetchOptionalInto(Booking.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Booking> findBookingsByVenue(Long venueId) {
    return dsl.selectFrom(BOOKING)
        .where(BOOKING.VENUE_ID.eq(venueId))
        .orderBy(BOOKING.SHOW_DATE.desc(), BOOKING.ID.desc())
        .fetchInto(Booking.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Booking> findBookingsByOnchainStatus(List<String> onchainStatuses) {
    return dsl.selectFrom(BOOKING)
        .where(BOOKING.ONCHAIN_STATUS.in(onchainStatuses))
        .orderBy(BOOKING.SHOW_DATE, BOOKING.ID)
        .fetchInto(Booking.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Concert> findConcertByBooking(Long bookingId) {
    return dsl.selectFrom(CONCERT)
        .where(CONCERT.BOOKING_ID.eq(bookingId))
        .fetchOptionalInto(Concert.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Long> findLatestCycleId(Long bandId) {
    return dsl.select(SPP_CYCLE.ID)
        .from(SPP_CYCLE)
        .where(SPP_CYCLE.BAND_ID.eq(bandId))
        .orderBy(SPP_CYCLE.ID.desc())
        .limit(1)
        .fetchOptional(SPP_CYCLE.ID);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public Concert createConcertForBooking(Booking booking, Long sppCycleId) {
    ConcertRecord rec = dsl.newRecord(CONCERT);
    rec.setBookingId(booking.getId());
    rec.setVenueId(booking.getVenueId());
    rec.setBandId(booking.getBandId());
    rec.setSppCycleId(sppCycleId);
    rec.setStatus("scheduled");
    rec.store();
    dsl.update(BOOKING)
        .set(BOOKING.ONCHAIN_CONCERT_ID, rec.getId())
        .where(BOOKING.ID.eq(booking.getId()))
        .execute();
    return rec.into(Concert.class);
  }

  /** {@inheritDoc} */
  @Override
  public Booking markProposed(
      Long bookingId,
      Long concertId,
      BigDecimal grossUsdc,
      String accessPda,
      String escrowPda,
      String txSignature) {
    dsl.update(BOOKING)
        .set(BOOKING.ONCHAIN_CONCERT_ID, concertId)
        .set(BOOKING.GROSS_USDC, grossUsdc)
        .set(BOOKING.VENUE_ACCESS_TOKEN_PDA, accessPda)
        .set(BOOKING.ESCROW_PDA, escrowPda)
        .set(BOOKING.ONCHAIN_STATUS, "proposed")
        .set(BOOKING.PROPOSE_TX_SIGNATURE, txSignature)
        .setNull(BOOKING.CONFIRM_TX_SIGNATURE)
        .setNull(BOOKING.CONTRACT_HASH)
        .setNull(BOOKING.CONTRACT_TEXT)
        .where(BOOKING.ID.eq(bookingId))
        .execute();
    return findBooking(bookingId).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public Booking markConfirmed(
      Long bookingId, String contractText, String contractHashHex, String txSignature) {
    dsl.update(BOOKING)
        .set(BOOKING.CONTRACT_TEXT, contractText)
        .set(BOOKING.CONTRACT_HASH, contractHashHex)
        .set(BOOKING.ONCHAIN_STATUS, "confirmed")
        .set(BOOKING.CONFIRM_TX_SIGNATURE, txSignature)
        .set(BOOKING.STATUS, "confirmed")
        .where(BOOKING.ID.eq(bookingId))
        .execute();
    return findBooking(bookingId).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public Booking markCancelled(Long bookingId, String txSignature) {
    dsl.update(BOOKING)
        .set(BOOKING.ONCHAIN_STATUS, "cancelled")
        .set(BOOKING.CANCEL_TX_SIGNATURE, txSignature)
        .setNull(BOOKING.VENUE_ACCESS_TOKEN_PDA)
        .setNull(BOOKING.ESCROW_PDA)
        .where(BOOKING.ID.eq(bookingId))
        .execute();
    return findBooking(bookingId).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal sumExpenses(Long concertId) {
    BigDecimal total =
        dsl.select(DSL.sum(CONCERT_EXPENSE.AMOUNT_USDC))
            .from(CONCERT_EXPENSE)
            .where(CONCERT_EXPENSE.CONCERT_ID.eq(concertId))
            .fetchOne(0, BigDecimal.class);
    return total == null ? BigDecimal.ZERO : total;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<ConcertSettlement> findSettlementByConcert(Long concertId) {
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.CONCERT_ID.eq(concertId))
        .fetchOptionalInto(ConcertSettlement.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ConcertSettlement markSettledFromEscrow(
      Long settlementId,
      Long bookingId,
      String signerWallet,
      String txSignature,
      String concertSettlementPda) {
    dsl.update(CONCERT_SETTLEMENT)
        .set(CONCERT_SETTLEMENT.SETTLE_TX_SIGNATURE, txSignature)
        .set(CONCERT_SETTLEMENT.SETTLED_AT, LocalDateTime.now())
        .set(CONCERT_SETTLEMENT.ON_CHAIN_STATUS, "confirmed")
        .set(CONCERT_SETTLEMENT.SETTLED_VIA, "settle_booking")
        .set(CONCERT_SETTLEMENT.SETTLED_BY_WALLET, signerWallet)
        .where(CONCERT_SETTLEMENT.ID.eq(settlementId))
        .execute();
    Long concertId =
        dsl.select(CONCERT_SETTLEMENT.CONCERT_ID)
            .from(CONCERT_SETTLEMENT)
            .where(CONCERT_SETTLEMENT.ID.eq(settlementId))
            .fetchOne(CONCERT_SETTLEMENT.CONCERT_ID);
    dsl.update(CONCERT)
        .set(CONCERT.CONCERT_SETTLEMENT_PDA, concertSettlementPda)
        .set(CONCERT.STATUS, "settled")
        .where(CONCERT.ID.eq(concertId))
        .execute();
    dsl.update(BOOKING)
        .set(BOOKING.ONCHAIN_STATUS, "settled")
        .set(BOOKING.STATUS, "played")
        .where(BOOKING.ID.eq(bookingId))
        .execute();
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.ID.eq(settlementId))
        .fetchOneInto(ConcertSettlement.class);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecorded(String txSignature) {
    return dsl.fetchExists(
            VENUE,
            VENUE.REGISTER_TX_SIGNATURE.eq(txSignature).or(VENUE.APPROVE_TX_SIGNATURE.eq(txSignature)))
        || dsl.fetchExists(
            BOOKING,
            BOOKING
                .PROPOSE_TX_SIGNATURE
                .eq(txSignature)
                .or(BOOKING.CONFIRM_TX_SIGNATURE.eq(txSignature))
                .or(BOOKING.CANCEL_TX_SIGNATURE.eq(txSignature)))
        || dsl.fetchExists(CONCERT_SETTLEMENT, CONCERT_SETTLEMENT.SETTLE_TX_SIGNATURE.eq(txSignature));
  }
}
