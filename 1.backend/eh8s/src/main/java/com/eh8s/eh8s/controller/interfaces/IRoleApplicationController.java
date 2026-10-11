package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;

/**
 * HTTP contract for role apply and owner approval.
 */
public interface IRoleApplicationController {

  /**
   * Applies for an applyable role.
   *
   * @param request application JSON ({@code walletPubkey}, {@code role})
   * @return pending application
   */
  AccountRoleApplication applyRole(AccountRoleApplication request);

  /**
   * Lists applications (owner pending queue or filtered).
   *
   * @param status optional status
   * @param walletPubkey optional wallet
   * @return applications
   */
  List<AccountRoleApplication> listApplications(String status, String walletPubkey);

  /**
   * Approves a pending application. Reviewer is the JWT session; body wallet is not trusted.
   *
   * @param principal signed-in account
   * @param id application id
   * @param review optional JSON ({@code reason} only; {@code reviewedByWallet} ignored for authz)
   * @return updated application
   */
  AccountRoleApplication approve(
      JwtPrincipal principal, Long id, AccountRoleApplication review);

  /**
   * Rejects a pending application. Reviewer is the JWT session; body wallet is not trusted.
   *
   * @param principal signed-in account
   * @param id application id
   * @param review optional JSON ({@code reason} only; {@code reviewedByWallet} ignored for authz)
   * @return updated application
   */
  AccountRoleApplication reject(
      JwtPrincipal principal, Long id, AccountRoleApplication review);

  /**
   * Revokes an approved role. Reviewer is the JWT session; body wallet is not trusted.
   *
   * @param principal signed-in account
   * @param id application id
   * @param review optional JSON ({@code reason} only; {@code reviewedByWallet} ignored for authz)
   * @return removed application
   */
  AccountRoleApplication revoke(
      JwtPrincipal principal, Long id, AccountRoleApplication review);

  /**
   * Lists approved memberships for a wallet.
   *
   * @param walletPubkey wallet
   * @return memberships
   */
  List<AccountRole> listMemberships(String walletPubkey);

  /**
   * Grants studio admin or partner. The signed-in wallet is the reviewer.
   *
   * @param principal signed-in account
   * @param request application JSON ({@code walletPubkey}, {@code role})
   * @return approved application
   */
  AccountRoleApplication grant(JwtPrincipal principal, AccountRoleApplication request);
}
