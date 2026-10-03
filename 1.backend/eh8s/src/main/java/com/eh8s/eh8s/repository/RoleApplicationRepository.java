package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s_role.Tables.ACCOUNT_ROLE;
import static com.eh8s.eh8s.database.jooq.eh8s_role.Tables.ACCOUNT_ROLE_APPLICATION;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.records.AccountRoleApplicationRecord;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.records.AccountRoleRecord;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Condition;
import org.springframework.stereotype.Repository;

/**
 * JOOQ persistence for role applications and memberships.
 */
@Repository
public class RoleApplicationRepository implements IRoleApplicationRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public RoleApplicationRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Account> findAccountByWallet(String walletPubkey) {
    return dsl.selectFrom(ACCOUNT)
        .where(ACCOUNT.WALLET_PUBKEY.eq(walletPubkey))
        .fetchOptionalInto(Account.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Account> findAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOptionalInto(Account.class);
  }

  /** {@inheritDoc} */
  @Override
  public AccountRoleApplication insertApplication(AccountRoleApplication application) {
    AccountRoleApplicationRecord rec = dsl.newRecord(ACCOUNT_ROLE_APPLICATION, application);
    rec.changed(ACCOUNT_ROLE_APPLICATION.ID, false);
    rec.store();
    return rec.into(AccountRoleApplication.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<AccountRoleApplication> findApplication(Long id) {
    return dsl.selectFrom(ACCOUNT_ROLE_APPLICATION)
        .where(ACCOUNT_ROLE_APPLICATION.ID.eq(id))
        .fetchOptionalInto(AccountRoleApplication.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<AccountRoleApplication> findApplications(String status, String walletPubkey) {
    Condition condition = ACCOUNT_ROLE_APPLICATION.ID.isNotNull();
    if (status != null && !status.isBlank()) {
      condition = condition.and(ACCOUNT_ROLE_APPLICATION.STATUS.eq(status.trim().toLowerCase()));
    }
    if (walletPubkey != null && !walletPubkey.isBlank()) {
      condition = condition.and(ACCOUNT_ROLE_APPLICATION.WALLET_PUBKEY.eq(walletPubkey.trim()));
    }
    return dsl.selectFrom(ACCOUNT_ROLE_APPLICATION)
        .where(condition)
        .orderBy(ACCOUNT_ROLE_APPLICATION.CREATED_AT.desc())
        .fetchInto(AccountRoleApplication.class);
  }

  /** {@inheritDoc} */
  @Override
  public AccountRoleApplication updateApplicationStatus(
      Long id, String status, String reason, String reviewedByWallet) {
    dsl.update(ACCOUNT_ROLE_APPLICATION)
        .set(ACCOUNT_ROLE_APPLICATION.STATUS, status)
        .set(ACCOUNT_ROLE_APPLICATION.REASON, reason)
        .set(ACCOUNT_ROLE_APPLICATION.REVIEWED_BY_WALLET, reviewedByWallet)
        .set(ACCOUNT_ROLE_APPLICATION.REVIEWED_AT, LocalDateTime.now())
        .where(ACCOUNT_ROLE_APPLICATION.ID.eq(id))
        .execute();
    return dsl.selectFrom(ACCOUNT_ROLE_APPLICATION)
        .where(ACCOUNT_ROLE_APPLICATION.ID.eq(id))
        .fetchOneInto(AccountRoleApplication.class);
  }

  /** {@inheritDoc} */
  @Override
  public AccountRole insertMembership(AccountRole membership) {
    AccountRoleRecord rec = dsl.newRecord(ACCOUNT_ROLE, membership);
    rec.changed(ACCOUNT_ROLE.ID, false);
    rec.store();
    return rec.into(AccountRole.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<AccountRole> findMembershipsByAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT_ROLE)
        .where(ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId))
        .orderBy(ACCOUNT_ROLE.GRANTED_AT.asc())
        .fetchInto(AccountRole.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<AccountRole> findMembership(Long accountId, String role) {
    return dsl.selectFrom(ACCOUNT_ROLE)
        .where(ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId).and(ACCOUNT_ROLE.ROLE.eq(role)))
        .fetchOptionalInto(AccountRole.class);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteMembership(Long accountId, String role) {
    dsl.deleteFrom(ACCOUNT_ROLE)
        .where(ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId).and(ACCOUNT_ROLE.ROLE.eq(role)))
        .execute();
  }

  /** {@inheritDoc} */
  @Override
  public void deleteApplication(Long id) {
    dsl.deleteFrom(ACCOUNT_ROLE_APPLICATION)
        .where(ACCOUNT_ROLE_APPLICATION.ID.eq(id))
        .execute();
  }
}
