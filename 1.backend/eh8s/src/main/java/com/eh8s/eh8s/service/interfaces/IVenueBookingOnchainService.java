package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.util.Map;

/**
 * On-chain venue listing (register / approve) and VenueAccessToken booking escrow (propose,
 * confirm, cancel, settle). The backend never signs: it builds instructions and verifies the
 * DevNet signature before storing anything.
 */
public interface IVenueBookingOnchainService {

  /**
   * One venue with its listing state and bookings, for the venue's own screen.
   *
   * @param venueId venue id
   * @return venue, bookings and program id
   */
  Map<String, Object> venueConsole(Long venueId);

  /**
   * Builds {@code register_venue} for the venue wallet.
   *
   * @param venueId venue id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildRegister(Long venueId,String walletPubkey);

  /**
   * Verifies {@code register_venue} and stores the listing as {@code pending}.
   *
   * @param venueId venue id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated venue
   */
  Venue confirmRegister(Long venueId,String walletPubkey, String txSignature);

  /**
   * Pending venue listings and proposed bookings for the owner or a STAGE agent.
   *
   * @param walletPubkey signer wallet
   * @return signer role and the two queues
   */
  Map<String, Object> stageQueue(String walletPubkey);

  /**
   * Builds {@code approve_venue} (owner or STAGE agent).
   *
   * @param venueId venue id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildApprove(Long venueId,String walletPubkey);

  /**
   * Verifies {@code approve_venue} and stores the listing as {@code approved}.
   *
   * @param venueId venue id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated venue
   */
  Venue confirmApprove(Long venueId,String walletPubkey, String txSignature);

  /**
   * Builds {@code propose_booking}; creates the booking's concert once so its id seeds the
   * settlement PDA.
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @param grossUsdc gross concert income in USDC (at most 2 decimals)
   * @return instruction payload
   */
  Map<String, Object> buildPropose(Long bookingId,String walletPubkey, BigDecimal grossUsdc);

  /**
   * Verifies {@code propose_booking} and stores the escrow.
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @param grossUsdc gross concert income in USDC (at most 2 decimals)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated booking
   */
  Booking confirmPropose(Long bookingId,String walletPubkey, BigDecimal grossUsdc, String txSignature);

  /**
   * Builds {@code confirm_booking} with the SHA-256 of the canonical contract text.
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload plus {@code contractText}
   */
  Map<String, Object> buildConfirm(Long bookingId,String walletPubkey);

  /**
   * Verifies {@code confirm_booking} and stores the contract text and hash.
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated booking
   */
  Booking confirmConfirm(Long bookingId,String walletPubkey, String txSignature);

  /**
   * Builds {@code cancel_booking} (venue only, while proposed).
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildCancel(Long bookingId,String walletPubkey);

  /**
   * Verifies {@code cancel_booking} and frees the slot.
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated booking
   */
  Booking confirmCancel(Long bookingId,String walletPubkey, String txSignature);

  /**
   * Confirmed bookings waiting for a settlement, for the owner or a VAULT agent.
   *
   * @param walletPubkey signer wallet
   * @return signer role and bookings
   */
  Map<String, Object> vaultQueue(String walletPubkey);

  /**
   * Builds {@code settle_booking} from the escrow (owner or VAULT agent).
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload with the fee / pool preview
   */
  Map<String, Object> buildSettle(Long bookingId,String walletPubkey);

  /**
   * Verifies {@code settle_booking}, stores the settlement and pending member claims.
   *
   * @param bookingId booking id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return confirmed settlement
   */
  ConcertSettlement confirmSettle(Long bookingId,String walletPubkey, String txSignature);
}
