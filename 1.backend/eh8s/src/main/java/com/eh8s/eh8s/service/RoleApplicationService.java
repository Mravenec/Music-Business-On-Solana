package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import com.eh8s.eh8s.service.interfaces.IRoleApplicationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Applies, lists, and owner-reviews platform roles. Owner is never applyable.
 */
@Service
public class RoleApplicationService implements IRoleApplicationService {

  private static final Set<String> APPLYABLE_ROLES =
      Set.of("student", "musician", "instructor", "venue");
  private static final String STATUS_PENDING = "pending";
  private static final String STATUS_APPROVED = "approved";
  private static final String STATUS_REJECTED = "rejected";

  private final IRoleApplicationRepository roleApplicationRepository;
  private final IChainConfigRepository chainConfigRepository;

  /**
   * Creates the service.
   *
   * @param roleApplicationRepository role persistence
   * @param chainConfigRepository chain config for owner wallet
   */
  public RoleApplicationService(
      IRoleApplicationRepository roleApplicationRepository,
      IChainConfigRepository chainConfigRepository) {
    this.roleApplicationRepository = roleApplicationRepository;
    this.chainConfigRepository = chainConfigRepository;
  }

  /** {@inheritDoc} */
  @Override
  public AccountRoleApplication applyRole(AccountRoleApplication request) {
    if (request == null
        || request.getWalletPubkey() == null
        || request.getWalletPubkey().isBlank()
        || request.getRole() == null
        || request.getRole().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey and role required");
    }
    String wallet = request.getWalletPubkey().trim();
    String role = request.getRole().trim().toLowerCase(Locale.ROOT);
    if ("owner".equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner role is not applyable");
    }
    if (!APPLYABLE_ROLES.contains(role)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Role must be student, musician, instructor, or venue");
    }
    if (isPlatformOwnerWallet(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Platform owner wallet does not apply for roles");
    }
    Account account =
        roleApplicationRepository
            .findAccountByWallet(wallet)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account for wallet — connect session first"));
    if (roleApplicationRepository.findMembership(account.getId(), role).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already granted");
    }
    AccountRoleApplication row = new AccountRoleApplication();
    row.setAccountId(account.getId());
    row.setWalletPubkey(wallet);
    row.setRole(role);
    row.setStatus(STATUS_PENDING);
    row.setCreatedAt(LocalDateTime.now());
    try {
      return roleApplicationRepository.insertApplication(row);
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Application already exists for this wallet and role");
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<AccountRoleApplication> listApplications(String status, String walletPubkey) {
    return roleApplicationRepository.findApplications(status, walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  public AccountRoleApplication approve(Long applicationId, AccountRoleApplication review) {
    assertOwnerReviewer(review);
    AccountRoleApplication app = requirePending(applicationId);
    AccountRoleApplication updated =
        roleApplicationRepository.updateApplicationStatus(
            applicationId,
            STATUS_APPROVED,
            review.getReason(),
            review.getReviewedByWallet().trim());
    if (roleApplicationRepository.findMembership(app.getAccountId(), app.getRole()).isEmpty()) {
      AccountRole membership = new AccountRole();
      membership.setAccountId(app.getAccountId());
      membership.setRole(app.getRole());
      membership.setGrantedAt(LocalDateTime.now());
      membership.setApplicationId(app.getId());
      roleApplicationRepository.insertMembership(membership);
    }
    return updated;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Deletes the membership before the application because the membership row points at it.
   * Removing the application lets the same wallet apply for that role again.
   */
  @Override
  public AccountRoleApplication revoke(Long applicationId, AccountRoleApplication review) {
    assertOwnerReviewer(review);
    AccountRoleApplication app =
        roleApplicationRepository
            .findApplication(applicationId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    if (!STATUS_APPROVED.equalsIgnoreCase(app.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an approved role can be revoked");
    }
    roleApplicationRepository.deleteMembership(app.getAccountId(), app.getRole());
    roleApplicationRepository.deleteApplication(app.getId());
    app.setStatus("revoked");
    app.setReason(review.getReason());
    app.setReviewedByWallet(review.getReviewedByWallet().trim());
    app.setReviewedAt(LocalDateTime.now());
    return app;
  }

  /** {@inheritDoc} */
  @Override
  public AccountRoleApplication reject(Long applicationId, AccountRoleApplication review) {
    assertOwnerReviewer(review);
    requirePending(applicationId);
    return roleApplicationRepository.updateApplicationStatus(
        applicationId,
        STATUS_REJECTED,
        review.getReason(),
        review.getReviewedByWallet().trim());
  }

  /** {@inheritDoc} */
  @Override
  public List<AccountRole> listMemberships(String walletPubkey) {
    if (walletPubkey == null || walletPubkey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey required");
    }
    Account account =
        roleApplicationRepository
            .findAccountByWallet(walletPubkey.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account"));
    return roleApplicationRepository.findMembershipsByAccount(account.getId());
  }

  private AccountRoleApplication requirePending(Long applicationId) {
    AccountRoleApplication app =
        roleApplicationRepository
            .findApplication(applicationId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    if (!STATUS_PENDING.equalsIgnoreCase(app.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Application is not pending");
    }
    return app;
  }

  private void assertOwnerReviewer(AccountRoleApplication review) {
    if (review == null
        || review.getReviewedByWallet() == null
        || review.getReviewedByWallet().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewedByWallet required");
    }
    if (!isPlatformOwnerWallet(review.getReviewedByWallet().trim())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only chain_config owner wallet may approve or reject");
    }
  }

  private boolean isPlatformOwnerWallet(String wallet) {
    return chainConfigRepository
        .findActive()
        .map(ChainConfig::getOwnerWalletPubkey)
        .filter(owner -> owner != null && !owner.isBlank())
        .map(owner -> owner.equals(wallet.trim()))
        .orElse(false);
  }
}
