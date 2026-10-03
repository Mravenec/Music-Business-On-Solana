package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import java.util.List;

/**
 * Role apply / owner approve-reject use cases.
 */
public interface IRoleApplicationService {

  /**
   * Submits a role application for a non-owner wallet.
   *
   * @param request application POJO carrying {@code walletPubkey} and {@code role}
   * @return stored pending application
   */
  AccountRoleApplication applyRole(AccountRoleApplication request);

  /**
   * Lists applications, typically pending for the owner queue.
   *
   * @param status optional status filter
   * @param walletPubkey optional applicant filter
   * @return applications
   */
  List<AccountRoleApplication> listApplications(String status, String walletPubkey);

  /**
   * Approves a pending application (platform owner wallet only).
   *
   * @param applicationId application id
   * @param review application POJO carrying {@code reviewedByWallet} and optional {@code reason}
   * @return updated application
   */
  AccountRoleApplication approve(Long applicationId, AccountRoleApplication review);

  /**
   * Rejects a pending application (platform owner wallet only).
   *
   * @param applicationId application id
   * @param review application POJO carrying {@code reviewedByWallet} and optional {@code reason}
   * @return updated application
   */
  AccountRoleApplication reject(Long applicationId, AccountRoleApplication review);

  /**
   * Revokes an approved role (platform owner wallet only). The grant and the application
   * row are removed so the wallet can apply again.
   *
   * @param applicationId approved application id
   * @param review application POJO carrying {@code reviewedByWallet} and optional {@code reason}
   * @return the removed application with status {@code revoked}
   */
  AccountRoleApplication revoke(Long applicationId, AccountRoleApplication review);

  /**
   * Lists approved multi-role memberships for a wallet.
   *
   * @param walletPubkey applicant wallet
   * @return memberships
   */
  List<AccountRole> listMemberships(String walletPubkey);

  /**
   * Grants studio admin or partner to a wallet that already has an account.
   * Studio admin can be granted only by the principal wallet.
   *
   * @param reviewerAccountId signed-in reviewer
   * @param request application POJO carrying {@code walletPubkey} and {@code role}
   * @return the approved application
   */
  AccountRoleApplication grant(Long reviewerAccountId, AccountRoleApplication request);

  /**
   * Records the partner role for a wallet that was added to the books. No account means nothing is stored yet.
   *
   * @param walletPubkey partner wallet
   */
  void ensurePartner(String walletPubkey);

  /**
   * Removes the partner role when that wallet leaves the books. The principal wallet is left alone.
   *
   * @param walletPubkey partner wallet
   */
  void clearPartner(String walletPubkey);
}
