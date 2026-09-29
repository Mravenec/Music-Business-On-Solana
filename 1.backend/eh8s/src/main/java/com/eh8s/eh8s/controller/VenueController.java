package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IVenueController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.service.interfaces.IVenueService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves stage map and settlement JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class VenueController implements IVenueController {

  private final IVenueService venueService;

  /**
   * Creates the controller.
   *
   * @param venueService venue use cases
   */
  public VenueController(IVenueService venueService) {
    this.venueService = venueService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/venues")
  public List<Venue> venues() {
    return venueService.venues();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/venues")
  public Venue createVenue(@RequestBody Venue venue) {
    return venueService.createVenue(venue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/contract-types")
  public List<ContractType> contractTypes() {
    return venueService.contractTypes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bookings")
  public List<Booking> bookings() {
    return venueService.bookings();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/bookings")
  public Booking createBooking(@RequestBody Booking booking) {
    return venueService.createBooking(booking);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/concerts")
  public List<Concert> concerts() {
    return venueService.concerts();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/concerts")
  public Concert createConcert(@RequestBody Concert concert) {
    return venueService.createConcert(concert);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/concerts/{concertId}/settle")
  public ConcertSettlement settle(
      @PathVariable Long concertId, @RequestBody(required = false) ConcertSettlement input) {
    BigDecimal gross =
        input == null || input.getGrossUsdc() == null ? new BigDecimal("875.00") : input.getGrossUsdc();
    return venueService.settle(concertId, gross);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/settlements/{settlementId}/claims")
  public List<PendingClaim> claims(@PathVariable Long settlementId) {
    return venueService.claims(settlementId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/pending-claims/{claimId}/claim")
  public PendingClaim markClaim(@PathVariable Long claimId) {
    return venueService.markClaim(claimId);
  }
}
