package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import java.util.List;

/**
 * Studio earnings recorded for the signed-in wallet during one calendar month.
 */
public interface IStudioLedgerService {

  /**
   * Confirmed royalty claims paid to the account wallet in that month.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return claims, or an empty list when the account has no wallet
   */
  List<PendingClaim> confirmedClaims(Long accountId, int year, int month);

  /**
   * Confirmed instructor shares paid to the account wallet in that month.
   *
   * @param accountId signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return subscriptions, or an empty list when the account has no wallet
   */
  List<AcademySubscription> instructorShares(Long accountId, int year, int month);
}
