package com.eh8s.eh8s.service;

import java.util.Set;

/**
 * Who may grant or revoke studio admin and partner beside the principal wallet.
 */
public final class StudioAccess {

  /** Granted administration. Cannot replace the principal wallet. */
  public static final String STUDIO_ADMIN = "studio_admin";

  /** Granted share. Sees their own studio accounting line. */
  public static final String PARTNER = "partner";

  private static final Set<String> ORDINARY =
      Set.of("student", "musician", "instructor", "venue", PARTNER);

  private StudioAccess() {}

  /**
   * A direct grant of studio admin is principal-only. A partner grant is the principal or a studio admin.
   *
   * @param principal reviewer wallet is the chain owner
   * @param studioAdmin reviewer holds studio admin, or is the principal
   * @param role role being granted
   * @return whether that grant is allowed
   */
  public static boolean canGrant(boolean principal, boolean studioAdmin, String role) {
    if (role == null || "owner".equals(role)) {
      return false;
    }
    if (STUDIO_ADMIN.equals(role)) {
      return principal;
    }
    if (PARTNER.equals(role)) {
      return principal || studioAdmin;
    }
    return false;
  }

  /**
   * Review of an ordinary role is open to a studio admin. Studio admin itself is principal-only.
   * The principal wallet cannot be revoked.
   *
   * @param principal reviewer wallet is the chain owner
   * @param studioAdmin reviewer holds studio admin, or is the principal
   * @param role role on the application
   * @param targetIsPrincipal the applicant wallet is the chain owner
   * @return whether that review is allowed
   */
  public static boolean canReview(
      boolean principal, boolean studioAdmin, String role, boolean targetIsPrincipal) {
    if (targetIsPrincipal || role == null || "owner".equals(role)) {
      return false;
    }
    if (STUDIO_ADMIN.equals(role)) {
      return principal;
    }
    return (principal || studioAdmin) && ORDINARY.contains(role);
  }
}
