package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Role review authorization uses the JWT session account, never body reviewedByWallet.
 */
class RoleApplicationServiceTest {

  private static final Long OWNER_ACCOUNT_ID = 1L;
  private static final Long STUDIO_ADMIN_ACCOUNT_ID = 2L;
  private static final Long SPOOFER_ACCOUNT_ID = 9L;
  private static final String OWNER_WALLET = "OwnerWallet";
  private static final String STUDIO_ADMIN_WALLET = "StudioAdminWallet";
  private static final String SPOOFER_WALLET = "SpooferWallet";

  @Test
  void revokeDeletesMembershipThenApplication() {
    FakeRoles repo = new FakeRoles();
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());
    AccountRoleApplication review = new AccountRoleApplication();
    review.setReason("left the roster");
    // Spoof body wallet must not matter when session is the principal.
    review.setReviewedByWallet(SPOOFER_WALLET);

    AccountRoleApplication result = service.revoke(OWNER_ACCOUNT_ID, 7L, review);

    assertEquals("revoked", result.getStatus());
    assertEquals(OWNER_WALLET, result.getReviewedByWallet());
    assertEquals(List.of("membership", "application"), repo.deleted);
    assertTrue(repo.applications.isEmpty());
    assertTrue(repo.memberships.isEmpty());
  }

  @Test
  void revokeRejectsAPendingApplication() {
    FakeRoles repo = new FakeRoles();
    repo.applications.get(0).setStatus("pending");
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());
    AccountRoleApplication review = new AccountRoleApplication();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.revoke(OWNER_ACCOUNT_ID, 7L, review));
    assertEquals(409, ex.getStatusCode().value());
    assertTrue(repo.deleted.isEmpty());
  }

  @Test
  void approveAllowsRealPrincipalSessionIgnoringBodyWallet() {
    FakeRoles repo = pendingStudent();
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());
    AccountRoleApplication review = new AccountRoleApplication();
    review.setReviewedByWallet(SPOOFER_WALLET);
    review.setReason("ok");

    AccountRoleApplication result = service.approve(OWNER_ACCOUNT_ID, 7L, review);

    assertEquals("approved", result.getStatus());
    assertEquals(OWNER_WALLET, result.getReviewedByWallet());
    assertTrue(repo.findMembership(3L, "student").isPresent());
  }

  @Test
  void approveAllowsStudioAdminSessionForOrdinaryRole() {
    FakeRoles repo = pendingStudent();
    repo.memberships.add(studioAdminMembership());
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());

    AccountRoleApplication result =
        service.approve(STUDIO_ADMIN_ACCOUNT_ID, 7L, new AccountRoleApplication());

    assertEquals("approved", result.getStatus());
    assertEquals(STUDIO_ADMIN_WALLET, result.getReviewedByWallet());
  }

  @Test
  void approveForbidsSpoofBodyWhenSessionIsNotReviewer() {
    FakeRoles repo = pendingStudent();
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());
    AccountRoleApplication review = new AccountRoleApplication();
    review.setReviewedByWallet(OWNER_WALLET);

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.approve(SPOOFER_ACCOUNT_ID, 7L, review));
    assertEquals(403, ex.getStatusCode().value());
    assertEquals("pending", repo.applications.get(0).getStatus());
  }

  @Test
  void rejectForbidsSpoofBodyWhenSessionIsNotReviewer() {
    FakeRoles repo = pendingStudent();
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());
    AccountRoleApplication review = new AccountRoleApplication();
    review.setReviewedByWallet(OWNER_WALLET);

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.reject(SPOOFER_ACCOUNT_ID, 7L, review));
    assertEquals(403, ex.getStatusCode().value());
  }

  @Test
  void studioAdminCannotApproveStudioAdminRole() {
    FakeRoles repo = pendingStudent();
    repo.applications.get(0).setRole(StudioAccess.STUDIO_ADMIN);
    repo.memberships.add(studioAdminMembership());
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.approve(STUDIO_ADMIN_ACCOUNT_ID, 7L, new AccountRoleApplication()));
    assertEquals(403, ex.getStatusCode().value());
  }

  @Test
  void confirmedGrantCachesStudioAdminAndConfirmedRevokeRemovesIt() {
    FakeRoles repo = new FakeRoles();
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());

    service.ensureStudioAdmin(SPOOFER_WALLET);
    assertTrue(repo.findMembership(SPOOFER_ACCOUNT_ID, StudioAccess.STUDIO_ADMIN).isPresent());
    service.ensureStudioAdmin(SPOOFER_WALLET);
    assertEquals(1L, repo.memberships.stream().filter(m -> StudioAccess.STUDIO_ADMIN.equals(m.getRole())).count());

    service.clearStudioAdmin(SPOOFER_WALLET);
    assertTrue(repo.findMembership(SPOOFER_ACCOUNT_ID, StudioAccess.STUDIO_ADMIN).isEmpty());

    // The principal wallet and an unknown wallet are left alone.
    service.ensureStudioAdmin(OWNER_WALLET);
    service.ensureStudioAdmin("NobodyYet");
    assertTrue(repo.findMembership(OWNER_ACCOUNT_ID, StudioAccess.STUDIO_ADMIN).isEmpty());
  }

  @Test
  void jwtGrantCannotBringBackAStudioAdminRevokedOnChain() {
    FakeRoles repo = new FakeRoles();
    StudioRoleGrant revoked =
        new StudioRoleGrant(1L, SPOOFER_WALLET, "studio_admin", LocalDateTime.now(), LocalDateTime.now(), "sig", "pda");
    RoleApplicationService service =
        new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), chain(revoked));
    AccountRoleApplication request = new AccountRoleApplication();
    request.setWalletPubkey(SPOOFER_WALLET);
    request.setRole(StudioAccess.STUDIO_ADMIN);

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.grant(OWNER_ACCOUNT_ID, request));
    assertEquals(409, ex.getStatusCode().value());
    assertTrue(repo.findMembership(SPOOFER_ACCOUNT_ID, StudioAccess.STUDIO_ADMIN).isEmpty());

    // With no on-chain row the MariaDB grant stays the fallback.
    RoleApplicationService fallback =
        new RoleApplicationService(repo, ownerConfig(OWNER_WALLET), noChain());
    fallback.grant(OWNER_ACCOUNT_ID, request);
    assertTrue(repo.findMembership(SPOOFER_ACCOUNT_ID, StudioAccess.STUDIO_ADMIN).isPresent());
  }

  private static IStudioPowerOnchainRepository noChain() {
    return chain(null);
  }

  private static IStudioPowerOnchainRepository chain(StudioRoleGrant row) {
    return (IStudioPowerOnchainRepository)
        Proxy.newProxyInstance(
            IStudioPowerOnchainRepository.class.getClassLoader(),
            new Class<?>[] {IStudioPowerOnchainRepository.class},
            (proxy, method, args) -> {
              if (method.getName().equals("findRoleGrant")) {
                return Optional.ofNullable(row).filter(r -> r.getWalletPubkey().equals(args[0]));
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static FakeRoles pendingStudent() {
    FakeRoles repo = new FakeRoles();
    repo.applications.get(0).setStatus("pending");
    repo.memberships.clear();
    return repo;
  }

  private static AccountRole studioAdminMembership() {
    AccountRole membership = new AccountRole();
    membership.setAccountId(STUDIO_ADMIN_ACCOUNT_ID);
    membership.setRole(StudioAccess.STUDIO_ADMIN);
    return membership;
  }

  private static IChainConfigRepository ownerConfig(String wallet) {
    ChainConfig config = new ChainConfig();
    config.setOwnerWalletPubkey(wallet);
    return () -> Optional.of(config);
  }

  private static final class FakeRoles implements IRoleApplicationRepository {
    final List<AccountRoleApplication> applications = new ArrayList<>();
    final List<AccountRole> memberships = new ArrayList<>();
    final List<String> deleted = new ArrayList<>();
    final List<Account> accounts = new ArrayList<>();

    FakeRoles() {
      accounts.add(account(OWNER_ACCOUNT_ID, OWNER_WALLET));
      accounts.add(account(STUDIO_ADMIN_ACCOUNT_ID, STUDIO_ADMIN_WALLET));
      accounts.add(account(SPOOFER_ACCOUNT_ID, SPOOFER_WALLET));
      AccountRoleApplication app = new AccountRoleApplication();
      app.setId(7L);
      app.setAccountId(3L);
      app.setRole("student");
      app.setStatus("approved");
      app.setWalletPubkey("StudentWallet");
      applications.add(app);
      AccountRole membership = new AccountRole();
      membership.setAccountId(3L);
      membership.setRole("student");
      memberships.add(membership);
    }

    private static Account account(Long id, String wallet) {
      Account row = new Account();
      row.setId(id);
      row.setWalletPubkey(wallet);
      return row;
    }

    @Override
    public Optional<Account> findAccountByWallet(String walletPubkey) {
      return accounts.stream()
          .filter(row -> walletPubkey != null && walletPubkey.equals(row.getWalletPubkey()))
          .findFirst();
    }

    @Override
    public Optional<Account> findAccount(Long accountId) {
      return accounts.stream().filter(row -> accountId.equals(row.getId())).findFirst();
    }

    @Override
    public AccountRoleApplication insertApplication(AccountRoleApplication application) {
      return application;
    }

    @Override
    public Optional<AccountRoleApplication> findApplication(Long id) {
      return applications.stream().filter(row -> id.equals(row.getId())).findFirst();
    }

    @Override
    public List<AccountRoleApplication> findApplications(String status, String walletPubkey) {
      return List.copyOf(applications);
    }

    @Override
    public AccountRoleApplication updateApplicationStatus(
        Long id, String status, String reason, String reviewedByWallet) {
      AccountRoleApplication row = findApplication(id).orElseThrow();
      row.setStatus(status);
      row.setReason(reason);
      row.setReviewedByWallet(reviewedByWallet);
      return row;
    }

    @Override
    public AccountRole insertMembership(AccountRole membership) {
      memberships.add(membership);
      return membership;
    }

    @Override
    public List<AccountRole> findMembershipsByAccount(Long accountId) {
      return memberships.stream().filter(row -> accountId.equals(row.getAccountId())).toList();
    }

    @Override
    public Optional<AccountRole> findMembership(Long accountId, String role) {
      return memberships.stream()
          .filter(row -> accountId.equals(row.getAccountId()) && role.equals(row.getRole()))
          .findFirst();
    }

    @Override
    public void deleteMembership(Long accountId, String role) {
      deleted.add("membership");
      memberships.removeIf(row -> accountId.equals(row.getAccountId()) && role.equals(row.getRole()));
    }

    @Override
    public void deleteApplication(Long id) {
      deleted.add("application");
      applications.removeIf(row -> id.equals(row.getId()));
    }
  }
}
