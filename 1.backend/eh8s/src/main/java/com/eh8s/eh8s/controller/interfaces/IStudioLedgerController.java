package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;

/**
 * HTTP for personal studio earnings and the owner's partner books.
 */
public interface IStudioLedgerController {

  /**
   * Confirmed solo-show claims for one month. The owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return claims for that wallet
   */
  List<PendingClaim> soloClaims(JwtPrincipal principal, int year, int month);

  /**
   * Confirmed band-show claims for one month. The owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return claims for that wallet
   */
  List<PendingClaim> bandClaims(JwtPrincipal principal, int year, int month);

  /**
   * Confirmed venue expense returns for one month. The owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return settlements for that venue wallet
   */
  List<ConcertSettlement> venueExpenses(JwtPrincipal principal, int year, int month);

  /**
   * Confirmed instructor shares for one month. The owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return subscriptions that paid this instructor wallet
   */
  List<AcademySubscription> instructorShares(JwtPrincipal principal, int year, int month);

  /**
   * This wallet's partner allocation for one month. The owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return that wallet's allocation rows
   */
  List<StudioPartnerAllocation> myAllocations(JwtPrincipal principal, int year, int month);

  /**
   * Active partners. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @return partner rows
   */
  List<StudioPartner> partners(JwtPrincipal principal);

  /**
   * Adds or updates a partner. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @param draft wallet, name, and shareBps
   * @return the stored row
   */
  StudioPartner savePartner(JwtPrincipal principal, StudioPartner draft);

  /**
   * Marks a partner inactive. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @param partnerId partner id
   * @return the stored row
   */
  StudioPartner deactivatePartner(JwtPrincipal principal, Long partnerId);

  /**
   * Confirmed show fees for one month. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return settlements that paid a studio fee
   */
  List<ConcertSettlement> showFees(JwtPrincipal principal, int year, int month);

  /**
   * Confirmed academy treasury rows for one month. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return subscriptions that paid the treasury
   */
  List<AcademySubscription> academyFees(JwtPrincipal principal, int year, int month);

  /**
   * Confirmed sync studio cuts for one month. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return deals that paid the studio
   */
  List<SyncLicenseDeal> syncFees(JwtPrincipal principal, int year, int month);

  /**
   * Every partner's allocation for one month. A non-owner receives 403.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return allocation rows
   */
  List<StudioPartnerAllocation> allocations(JwtPrincipal principal, int year, int month);
}
