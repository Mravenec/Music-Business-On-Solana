package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IVenueBookingOnchainController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.service.interfaces.IVenueBookingOnchainService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves venue listing and booking escrow build/confirm JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class VenueBookingOnchainController implements IVenueBookingOnchainController {

  private final IVenueBookingOnchainService service;

  /**
   * Creates the controller.
   *
   * @param service venue listing / booking escrow use cases
   */
  public VenueBookingOnchainController(IVenueBookingOnchainService service) {
    this.service = service;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/venues/{venueId}/onchain")
  public Map<String, Object> venueConsole(@PathVariable Long venueId) {
    return service.venueConsole(venueId);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/venues/{venueId}/register/build")
  public Map<String, Object> buildRegister(
      @PathVariable Long venueId, @RequestBody Map<String, Object> body) {
    return service.buildRegister(
        venueId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/venues/{venueId}/register/confirm")
  public Venue confirmRegister(@PathVariable Long venueId, @RequestBody Map<String, Object> body) {
    return service.confirmRegister(
        venueId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/stage/queue")
  public Map<String, Object> stageQueue(@RequestParam(required = false) String walletPubkey) {
    return service.stageQueue(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/venues/{venueId}/approve/build")
  public Map<String, Object> buildApprove(
      @PathVariable Long venueId, @RequestBody Map<String, Object> body) {
    return service.buildApprove(
        venueId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/venues/{venueId}/approve/confirm")
  public Venue confirmApprove(@PathVariable Long venueId, @RequestBody Map<String, Object> body) {
    return service.confirmApprove(
        venueId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/propose/build")
  public Map<String, Object> buildPropose(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.buildPropose(
        bookingId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "grossUsdc"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/propose/confirm")
  public Booking confirmPropose(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.confirmPropose(
        bookingId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "grossUsdc"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/confirm-contract/build")
  public Map<String, Object> buildConfirm(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.buildConfirm(
        bookingId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/confirm-contract/confirm")
  public Booking confirmConfirm(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.confirmConfirm(
        bookingId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/cancel/build")
  public Map<String, Object> buildCancel(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.buildCancel(
        bookingId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/cancel/confirm")
  public Booking confirmCancel(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.confirmCancel(
        bookingId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/vault/settle-queue")
  public Map<String, Object> vaultQueue(@RequestParam(required = false) String walletPubkey) {
    return service.vaultQueue(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/settle/build")
  public Map<String, Object> buildSettle(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.buildSettle(
        bookingId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bookings/{bookingId}/settle/confirm")
  public ConcertSettlement confirmSettle(
      @PathVariable Long bookingId, @RequestBody Map<String, Object> body) {
    return service.confirmSettle(
        bookingId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }
}
