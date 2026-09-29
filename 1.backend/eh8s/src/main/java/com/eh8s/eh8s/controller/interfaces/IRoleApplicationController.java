package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
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
   * Approves a pending application.
   *
   * @param id application id
   * @param review application JSON ({@code reviewedByWallet}, optional {@code reason})
   * @return updated application
   */
  AccountRoleApplication approve(Long id, AccountRoleApplication review);

  /**
   * Rejects a pending application.
   *
   * @param id application id
   * @param review application JSON ({@code reviewedByWallet}, optional {@code reason})
   * @return updated application
   */
  AccountRoleApplication reject(Long id, AccountRoleApplication review);

  /**
   * Revokes an approved role.
   *
   * @param id application id
   * @param review application JSON ({@code reviewedByWallet}, optional {@code reason})
   * @return removed application
   */
  AccountRoleApplication revoke(Long id, AccountRoleApplication review);

  /**
   * Lists approved memberships for a wallet.
   *
   * @param walletPubkey wallet
   * @return memberships
   */
  List<AccountRole> listMemberships(String walletPubkey);
}
