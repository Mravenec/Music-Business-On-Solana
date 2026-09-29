package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IRoleApplicationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owner revoke removes the grant and the application so the wallet can apply again.
 */
class RoleApplicationServiceTest {

  @Test
  void revokeDeletesMembershipThenApplication() {
    FakeRoles repo = new FakeRoles();
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig("OwnerWallet"));
    AccountRoleApplication review = new AccountRoleApplication();
    review.setReviewedByWallet("OwnerWallet");
    review.setReason("left the roster");

    AccountRoleApplication result = service.revoke(7L, review);

    assertEquals("revoked", result.getStatus());
    assertEquals(List.of("membership", "application"), repo.deleted);
    assertTrue(repo.applications.isEmpty());
    assertTrue(repo.memberships.isEmpty());
  }

  @Test
  void revokeRejectsAPendingApplication() {
    FakeRoles repo = new FakeRoles();
    repo.applications.get(0).setStatus("pending");
    RoleApplicationService service = new RoleApplicationService(repo, ownerConfig("OwnerWallet"));
    AccountRoleApplication review = new AccountRoleApplication();
    review.setReviewedByWallet("OwnerWallet");

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.revoke(7L, review));
    assertEquals(409, ex.getStatusCode().value());
    assertTrue(repo.deleted.isEmpty());
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

    FakeRoles() {
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

    @Override
    public Optional<Account> findAccountByWallet(String walletPubkey) {
      return Optional.empty();
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
      return findApplication(id).orElseThrow();
    }

    @Override
    public AccountRole insertMembership(AccountRole membership) {
      return membership;
    }

    @Override
    public List<AccountRole> findMembershipsByAccount(Long accountId) {
      return List.copyOf(memberships);
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
