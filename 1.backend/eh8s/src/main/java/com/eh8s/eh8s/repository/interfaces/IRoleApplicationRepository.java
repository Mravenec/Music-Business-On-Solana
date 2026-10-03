package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for role applications and multi-role membership.
 */
public interface IRoleApplicationRepository {

  /**
   * Finds an account by wallet pubkey.
   *
   * @param walletPubkey Solana wallet
   * @return account if present
   */
  Optional<Account> findAccountByWallet(String walletPubkey);

  /**
   * Finds an account by id.
   *
   * @param accountId account primary key
   * @return account if present
   */
  Optional<Account> findAccount(Long accountId);

  /**
   * Inserts a role application.
   *
   * @param application row without id
   * @return stored application
   */
  AccountRoleApplication insertApplication(AccountRoleApplication application);

  /**
   * Loads an application by id.
   *
   * @param id application primary key
   * @return application if present
   */
  Optional<AccountRoleApplication> findApplication(Long id);

  /**
   * Lists applications filtered by optional status and/or wallet.
   *
   * @param status pending|approved|rejected or null for all
   * @param walletPubkey optional applicant wallet filter
   * @return matching applications newest first
   */
  List<AccountRoleApplication> findApplications(String status, String walletPubkey);

  /**
   * Updates application status after owner review.
   *
   * @param id application id
   * @param status approved or rejected
   * @param reason optional reason
   * @param reviewedByWallet owner wallet
   * @return updated application
   */
  AccountRoleApplication updateApplicationStatus(
      Long id, String status, String reason, String reviewedByWallet);

  /**
   * Inserts an approved role membership (idempotent unique key).
   *
   * @param membership row without id
   * @return stored membership
   */
  AccountRole insertMembership(AccountRole membership);

  /**
   * Lists approved roles for an account.
   *
   * @param accountId account primary key
   * @return memberships
   */
  List<AccountRole> findMembershipsByAccount(Long accountId);

  /**
   * Finds an existing membership for account + role.
   *
   * @param accountId account id
   * @param role applyable role
   * @return membership if present
   */
  Optional<AccountRole> findMembership(Long accountId, String role);

  /**
   * Removes one granted role. The membership references the application, so this runs first.
   *
   * @param accountId account id
   * @param role applyable role
   */
  void deleteMembership(Long accountId, String role);

  /**
   * Removes the application row so the same wallet can apply for that role again.
   *
   * @param id application id
   */
  void deleteApplication(Long id);
}
