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
    AccountRoleApplication app = requirePending(applicationId);
    assertCanReview(review, app);
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
    AccountRoleApplication app =
        roleApplicationRepository
            .findApplication(applicationId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    assertCanReview(review, app);
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
    AccountRoleApplication pending = requirePending(applicationId);
    assertCanReview(review, pending);
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

  /** {@inheritDoc} */
  @Override
  public AccountRoleApplication grant(Long reviewerAccountId, AccountRoleApplication request) {
    if (request == null || request.getWalletPubkey() == null || request.getRole() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey and role required");
    }
    Account reviewer =
        roleApplicationRepository
            .findAccount(reviewerAccountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
    String role = request.getRole().trim().toLowerCase(Locale.ROOT);
    String wallet = request.getWalletPubkey().trim();
    boolean principal = isPlatformOwnerWallet(reviewer.getWalletPubkey());
    boolean studioAdmin = principal || holds(reviewer.getId(), StudioAccess.STUDIO_ADMIN);
    if (!StudioAccess.canGrant(principal, studioAdmin, role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That role grant is not yours to give");
    }
    if (isPlatformOwnerWallet(wallet)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The principal wallet is not a grant");
    }
    return storeGrant(wallet, role, reviewer.getWalletPubkey(), "Granted by the studio");
  }

  /** {@inheritDoc} */
  @Override
  public void ensurePartner(String walletPubkey) {
    if (walletPubkey == null || walletPubkey.isBlank() || isPlatformOwnerWallet(walletPubkey)) {
      return;
    }
    Account account = roleApplicationRepository.findAccountByWallet(walletPubkey.trim()).orElse(null);
    if (account == null || holds(account.getId(), StudioAccess.PARTNER)) {
      return;
    }
    String reviewer =
        chainConfigRepository
            .findActive()
            .map(ChainConfig::getOwnerWalletPubkey)
            .orElse(walletPubkey.trim());
    storeGrant(walletPubkey.trim(), StudioAccess.PARTNER, reviewer, "Partner share");
  }

  /** {@inheritDoc} */
  @Override
  public void clearPartner(String walletPubkey) {
    if (walletPubkey == null || walletPubkey.isBlank() || isPlatformOwnerWallet(walletPubkey)) {
      return;
    }
    Account account = roleApplicationRepository.findAccountByWallet(walletPubkey.trim()).orElse(null);
    if (account == null) {
      return;
    }
    roleApplicationRepository.deleteMembership(account.getId(), StudioAccess.PARTNER);
    roleApplicationRepository.findApplications(STATUS_APPROVED, walletPubkey.trim()).stream()
        .filter(row -> StudioAccess.PARTNER.equals(row.getRole()))
        .forEach(row -> roleApplicationRepository.deleteApplication(row.getId()));
  }

  private AccountRoleApplication storeGrant(
      String wallet, String role, String reviewerWallet, String reason) {
    Account account =
        roleApplicationRepository
            .findAccountByWallet(wallet)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "That wallet has not signed in yet"));
    if (holds(account.getId(), role)) {
      return roleApplicationRepository.findApplications(STATUS_APPROVED, wallet).stream()
          .filter(row -> role.equals(row.getRole()))
          .findFirst()
          .orElseThrow();
    }
    AccountRoleApplication row = new AccountRoleApplication();
    row.setAccountId(account.getId());
    row.setWalletPubkey(wallet);
    row.setRole(role);
    row.setStatus(STATUS_APPROVED);
    row.setReason(reason);
    row.setReviewedByWallet(reviewerWallet);
    row.setCreatedAt(LocalDateTime.now());
    row.setReviewedAt(LocalDateTime.now());
    AccountRoleApplication stored = roleApplicationRepository.insertApplication(row);
    AccountRole membership = new AccountRole();
    membership.setAccountId(account.getId());
    membership.setRole(role);
    membership.setGrantedAt(LocalDateTime.now());
    membership.setApplicationId(stored.getId());
    roleApplicationRepository.insertMembership(membership);
    return stored;
  }

  private void assertCanReview(AccountRoleApplication review, AccountRoleApplication app) {
    if (review == null
        || review.getReviewedByWallet() == null
        || review.getReviewedByWallet().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewedByWallet required");
    }
    String reviewerWallet = review.getReviewedByWallet().trim();
    boolean principal = isPlatformOwnerWallet(reviewerWallet);
    boolean studioAdmin = principal || reviewerHoldsStudioAdmin(reviewerWallet);
    boolean targetPrincipal =
        app.getWalletPubkey() != null && isPlatformOwnerWallet(app.getWalletPubkey());
    if (!StudioAccess.canReview(principal, studioAdmin, app.getRole(), targetPrincipal)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That review is not yours to make");
    }
  }

  private boolean reviewerHoldsStudioAdmin(String wallet) {
    Account account = roleApplicationRepository.findAccountByWallet(wallet).orElse(null);
    return account != null && holds(account.getId(), StudioAccess.STUDIO_ADMIN);
  }

  private boolean holds(Long accountId, String role) {
    return roleApplicationRepository.findMembership(accountId, role).isPresent();
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
