package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartnerAllocation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import java.util.List;

/**
 * Personal studio earnings for a non-owner, and partner books for the owner.
 */
public interface IStudioLedgerService {

  /**
   * Whether this account is the platform owner.
   *
   * @param accountId signed-in account
   * @return true for role owner or the chain-config owner wallet
   */
  boolean isOwner(Long accountId);

  /**
   * Whether this account may edit the partner books: the principal wallet or a studio admin.
   *
   * @param accountId signed-in account
   * @return true when the books are theirs to change
   */
  boolean isBooksEditor(Long accountId);

  /**
   * Confirmed solo-show claims for the account wallet in that month.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return claims, or an empty list when the account has no wallet
   */
  List<PendingClaim> soloClaims(Long accountId, int year, int month);

  /**
   * Confirmed band-show claims for the account wallet in that month.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return claims, or an empty list when the account has no wallet
   */
  List<PendingClaim> bandClaims(Long accountId, int year, int month);

  /**
   * Confirmed venue expense returns for the account wallet in that month.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return settlements, or an empty list when the account has no wallet
   */
  List<ConcertSettlement> venueExpenses(Long accountId, int year, int month);

  /**
   * Confirmed instructor shares for the account wallet in that month.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return subscriptions, or an empty list when the account has no wallet
   */
  List<AcademySubscription> instructorShares(Long accountId, int year, int month);

  /**
   * This wallet's partner allocation for the month, after the books are rewritten from recorded fees.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return that wallet's rows only
   */
  List<StudioPartnerAllocation> myAllocations(Long accountId, int year, int month);

  /**
   * Active partners.
   *
   * @return partner rows
   */
  List<StudioPartner> partners();

  /**
   * Adds a partner or updates the existing wallet. Active shares stay at or under 100%.
   *
   * @param draft wallet, name, and shareBps
   * @return the stored row
   */
  StudioPartner savePartner(StudioPartner draft);

  /**
   * Marks a partner inactive.
   *
   * @param partnerId partner id
   * @return the stored row
   */
  StudioPartner deactivatePartner(Long partnerId);

  /**
   * Rewrites the month's partner allocations, then returns confirmed show fees.
   *
   * @param year calendar year
   * @param month calendar month
   * @return settlements that paid a studio fee
   */
  List<ConcertSettlement> showFees(int year, int month);

  /**
   * Rewrites the month's partner allocations, then returns confirmed academy treasury rows.
   *
   * @param year calendar year
   * @param month calendar month
   * @return subscriptions that paid the treasury
   */
  List<AcademySubscription> academyFees(int year, int month);

  /**
   * Rewrites the month's partner allocations, then returns confirmed sync studio cuts.
   *
   * @param year calendar year
   * @param month calendar month
   * @return deals that paid the studio
   */
  List<SyncLicenseDeal> syncFees(int year, int month);

  /**
   * Rewrites the month's partner allocations, then returns every partner's rows.
   *
   * @param year calendar year
   * @param month calendar month
   * @return allocation rows
   */
  List<StudioPartnerAllocation> allocations(int year, int month);
}
