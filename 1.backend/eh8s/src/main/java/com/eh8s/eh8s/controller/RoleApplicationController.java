package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IRoleApplicationController;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRole;
import com.eh8s.eh8s.database.jooq.eh8s_role.tables.pojos.AccountRoleApplication;
import com.eh8s.eh8s.service.interfaces.IRoleApplicationService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoints for role applications and multi-role membership reads.
 */
@RestController
@RequestMapping("/api")
public class RoleApplicationController implements IRoleApplicationController {

  private final IRoleApplicationService roleApplicationService;

  /**
   * Creates the controller.
   *
   * @param roleApplicationService role use cases
   */
  public RoleApplicationController(IRoleApplicationService roleApplicationService) {
    this.roleApplicationService = roleApplicationService;
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/role-applications")
  public AccountRoleApplication applyRole(@RequestBody AccountRoleApplication request) {
    return roleApplicationService.applyRole(request);
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/role-applications")
  public List<AccountRoleApplication> listApplications(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String walletPubkey) {
    return roleApplicationService.listApplications(status, walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/role-applications/{id}/approve")
  public AccountRoleApplication approve(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long id,
      @RequestBody(required = false) AccountRoleApplication review) {
    return roleApplicationService.approve(accountId(principal), id, review);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/role-applications/{id}/reject")
  public AccountRoleApplication reject(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long id,
      @RequestBody(required = false) AccountRoleApplication review) {
    return roleApplicationService.reject(accountId(principal), id, review);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/role-applications/{id}/revoke")
  public AccountRoleApplication revoke(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long id,
      @RequestBody(required = false) AccountRoleApplication review) {
    return roleApplicationService.revoke(accountId(principal), id, review);
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/roles/memberships")
  public List<AccountRole> listMemberships(@RequestParam String walletPubkey) {
    return roleApplicationService.listMemberships(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/role-grants")
  public AccountRoleApplication grant(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody AccountRoleApplication request) {
    return roleApplicationService.grant(accountId(principal), request);
  }

  private static Long accountId(JwtPrincipal principal) {
    if (principal == null || principal.accountId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    return principal.accountId();
  }
}
