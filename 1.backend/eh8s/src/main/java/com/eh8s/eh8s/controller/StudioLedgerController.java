package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IStudioLedgerController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.service.interfaces.IStudioLedgerService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves personal earnings to a non-owner and partner books to the owner.
 */
@RestController
public class StudioLedgerController implements IStudioLedgerController {

  private final IStudioLedgerService ledgerService;

  /**
   * Creates the controller.
   *
   * @param ledgerService studio credit and partner use cases
   */
  public StudioLedgerController(IStudioLedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/solo-claims")
  public List<PendingClaim> soloClaims(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireUser(principal, year, month);
    return ledgerService.soloClaims(principal.accountId(), year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/band-claims")
  public List<PendingClaim> bandClaims(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireUser(principal, year, month);
    return ledgerService.bandClaims(principal.accountId(), year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/venue-expenses")
  public List<ConcertSettlement> venueExpenses(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireUser(principal, year, month);
    return ledgerService.venueExpenses(principal.accountId(), year, month);
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
    requireUser(principal, year, month);
    return ledgerService.instructorShares(principal.accountId(), year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/my-allocation")
  public List<StudioPartnerAllocation> myAllocations(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireUser(principal, year, month);
    return ledgerService.myAllocations(principal.accountId(), year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/partners")
  public List<StudioPartner> partners(@AuthenticationPrincipal JwtPrincipal principal) {
    requireOwner(principal);
    return ledgerService.partners();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/api/studio-ledger/partners")
  public StudioPartner savePartner(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody StudioPartner draft) {
    requireOwner(principal);
    return ledgerService.savePartner(draft);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DeleteMapping("/api/studio-ledger/partners/{partnerId}")
  public StudioPartner deactivatePartner(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long partnerId) {
    requireOwner(principal);
    return ledgerService.deactivatePartner(partnerId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/show-fees")
  public List<ConcertSettlement> showFees(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireOwnerMonth(principal, year, month);
    return ledgerService.showFees(year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/academy-fees")
  public List<AcademySubscription> academyFees(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireOwnerMonth(principal, year, month);
    return ledgerService.academyFees(year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/sync-fees")
  public List<SyncLicenseDeal> syncFees(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireOwnerMonth(principal, year, month);
    return ledgerService.syncFees(year, month);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/api/studio-ledger/allocations")
  public List<StudioPartnerAllocation> allocations(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    requireOwnerMonth(principal, year, month);
    return ledgerService.allocations(year, month);
  }

  private void requireUser(JwtPrincipal principal, int year, int month) {
    requireMonth(principal, year, month);
    if (ledgerService.isOwner(principal.accountId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owners use the partner books");
    }
  }

  private void requireOwnerMonth(JwtPrincipal principal, int year, int month) {
    requireMonth(principal, year, month);
    if (!ledgerService.isOwner(principal.accountId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can open the partner books");
    }
  }

  private void requireOwner(JwtPrincipal principal) {
    if (principal == null || principal.accountId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    if (!ledgerService.isOwner(principal.accountId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can open the partner books");
    }
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
