package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IStageMapController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import com.eh8s.eh8s.service.interfaces.IStageMapService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves Stage Map pins (colors from real data), venue open dates, and Request slot.
 */
@RestController
@RequestMapping("/api/stage-map")
public class StageMapController implements IStageMapController {

  private final IStageMapService stageMapService;

  /**
   * Creates the controller.
   *
   * @param stageMapService Stage Map use cases
   */
  public StageMapController(IStageMapService stageMapService) {
    this.stageMapService = stageMapService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/pins")
  public List<Map<String, Object>> pins(@RequestParam(required = false) String month) {
    return stageMapService.pins(parseMonth(month));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/venues/{venueId}")
  public Map<String, Object> pin(
      @PathVariable Long venueId, @RequestParam(required = false) String month) {
    return stageMapService.pin(venueId, parseMonth(month));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/venues/{venueId}/availability")
  public VenueAvailability addAvailability(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long venueId,
      @RequestBody VenueAvailability availability) {
    return stageMapService.addAvailability(accountId(principal), venueId, availability);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/venues/{venueId}/slot-requests")
  public Booking requestSlot(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long venueId,
      @RequestBody Booking booking) {
    return stageMapService.requestSlot(accountId(principal), venueId, booking);
  }

  private static YearMonth parseMonth(String month) {
    if (month == null || month.isBlank()) {
      return null;
    }
    try {
      return YearMonth.parse(month.trim());
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be YYYY-MM");
    }
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
