package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ISppInputsController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.service.interfaces.ISppInputsService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves creative ratings and concert set check-ins for the SPP inputs screens.
 */
@RestController
@RequestMapping("/api")
public class SppInputsController implements ISppInputsController {

  private final ISppInputsService sppInputsService;

  /**
   * Creates the controller.
   *
   * @param sppInputsService SPP inputs use cases
   */
  public SppInputsController(ISppInputsService sppInputsService) {
    this.sppInputsService = sppInputsService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/rehearsal-sessions/{sessionId}/creative-ratings")
  public List<CreativeRating> ratings(@PathVariable Long sessionId) {
    return sppInputsService.ratings(sessionId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/rehearsal-sessions/{sessionId}/creative-ratings")
  public CreativeRating rate(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long sessionId,
      @RequestBody CreativeRating rating) {
    return sppInputsService.rate(accountId(principal), sessionId, rating);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/concerts/{concertId}/set-checkins")
  public List<ConcertSetCheckin> setCheckins(@PathVariable Long concertId) {
    return sppInputsService.setCheckins(concertId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/concerts/{concertId}/set-checkins/start")
  public ConcertSetCheckin startSet(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long concertId,
      @RequestBody(required = false) ConcertSetCheckin checkin) {
    return sppInputsService.startSet(accountId(principal), concertId, checkin);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/concerts/{concertId}/set-checkins/end")
  public ConcertSetCheckin endSet(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long concertId,
      @RequestBody(required = false) ConcertSetCheckin checkin) {
    return sppInputsService.endSet(accountId(principal), concertId, checkin);
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
