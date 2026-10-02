package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IStudioLedgerController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves studio earnings for the signed-in wallet. The wallet balance itself is not returned.
 */
@RestController
public class StudioLedgerController implements IStudioLedgerController {

  private final IStudioLedgerService ledgerService;

  /**
   * Creates the controller.
   *
   * @param ledgerService studio credit use cases
   */
  public StudioLedgerController(IStudioLedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/claims")
  public List<PendingClaim> claims(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireMonth(principal, year, month);
    return ledgerService.confirmedClaims(principal.accountId(), year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/instructor-shares")
  public List<AcademySubscription> instructorShares(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireMonth(principal, year, month);
    return ledgerService.instructorShares(principal.accountId(), year, month);
  }

  private static void requireMonth(JwtPrincipal principal, int year, int month) {
    if (principal == null || principal.accountId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    if (year < 2000 || year > 2100 || month < 1 || month > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a real month");
    }
  }
}
