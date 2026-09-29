package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.util.Map;

/**
 * HTTP contract for on-chain venue listings and VenueAccessToken booking escrow (JWT Bearer).
 */
public interface IVenueBookingOnchainController {

  /**
   * GET {@code /api/venues/{venueId}/onchain}.
   *
   * @param venueId venue id
   * @return venue, bookings, program id
   */
  Map<String, Object> venueConsole(Long venueId);

  /**
   * POST {@code /api/venues/{venueId}/register/build}.
   *
   * @param venueId venue id
   * @param body {@code walletPubkey}
   * @return register_venue payload
   */
  Map<String, Object> buildRegister(Long venueId, Map<String, Object> body);

  /**
   * POST {@code /api/venues/{venueId}/register/confirm}.
   *
   * @param venueId venue id
   * @param body {@code walletPubkey}, {@code txSignature}
   * @return venue with listing {@code pending}
   */
  Venue confirmRegister(Long venueId, Map<String, Object> body);

  /**
   * GET {@code /api/stage/queue?walletPubkey=} (owner or STAGE agent).
   *
   * @param walletPubkey signer wallet
   * @return pending venues and proposed bookings
   */
  Map<String, Object> stageQueue(String walletPubkey);

  /**
   * POST {@code /api/venues/{venueId}/approve/build}.
   *
   * @param venueId venue id
   * @param body {@code walletPubkey}
   * @return approve_venue payload
   */
  Map<String, Object> buildApprove(Long venueId, Map<String, Object> body);

  /**
   * POST {@code /api/venues/{venueId}/approve/confirm}.
   *
   * @param venueId venue id
   * @param body {@code walletPubkey}, {@code txSignature}
   * @return venue with listing {@code approved}
   */
  Venue confirmApprove(Long venueId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/propose/build}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}, {@code grossUsdc}
   * @return propose_booking payload
   */
  Map<String, Object> buildPropose(Long bookingId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/propose/confirm}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}, {@code grossUsdc}, {@code txSignature}
   * @return booking with escrow {@code proposed}
   */
  Booking confirmPropose(Long bookingId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/confirm-contract/build}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}
   * @return confirm_booking payload plus contract text
   */
  Map<String, Object> buildConfirm(Long bookingId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/confirm-contract/confirm}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}, {@code txSignature}
   * @return booking with escrow {@code confirmed}
   */
  Booking confirmConfirm(Long bookingId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/cancel/build}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}
   * @return cancel_booking payload
   */
  Map<String, Object> buildCancel(Long bookingId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/cancel/confirm}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}, {@code txSignature}
   * @return booking with escrow {@code cancelled}
   */
  Booking confirmCancel(Long bookingId, Map<String, Object> body);

  /**
   * GET {@code /api/vault/settle-queue?walletPubkey=} (owner or VAULT agent).
   *
   * @param walletPubkey signer wallet
   * @return confirmed bookings
   */
  Map<String, Object> vaultQueue(String walletPubkey);

  /**
   * POST {@code /api/bookings/{bookingId}/settle/build}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}
   * @return settle_booking payload with fee / pool preview
   */
  Map<String, Object> buildSettle(Long bookingId, Map<String, Object> body);

  /**
   * POST {@code /api/bookings/{bookingId}/settle/confirm}.
   *
   * @param bookingId booking id
   * @param body {@code walletPubkey}, {@code txSignature}
   * @return confirmed settlement
   */
  ConcertSettlement confirmSettle(Long bookingId, Map<String, Object> body);
}
