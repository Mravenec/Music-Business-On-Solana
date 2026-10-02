package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;

/**
 * HTTP for studio earnings of the signed-in wallet.
 */
public interface IStudioLedgerController {

  /**
   * Confirmed royalty claims for one month.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return claims for that wallet
   */
  List<PendingClaim> claims(JwtPrincipal principal, int year, int month);

  /**
   * Confirmed instructor shares for one month.
   *
   * @param principal signed-in account
   * @param year calendar year
   * @param month calendar month, 1 through 12
   * @return subscriptions that paid this instructor wallet
   */
  List<AcademySubscription> instructorShares(JwtPrincipal principal, int year, int month);
}
