package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Studio admin can edit the books. Only the principal wallet grants that role or touches itself.
 */
class StudioAccessTest {

  @Test
  void onlyThePrincipalGrantsStudioAdmin() {
    assertTrue(StudioAccess.canGrant(true, true, StudioAccess.STUDIO_ADMIN));
    assertFalse(StudioAccess.canGrant(false, true, StudioAccess.STUDIO_ADMIN));
    assertTrue(StudioAccess.canGrant(false, true, StudioAccess.PARTNER));
    assertFalse(StudioAccess.canGrant(false, true, "owner"));
  }

  @Test
  void studioAdminCannotRevokeThePrincipalOrAnotherStudioAdmin() {
    assertFalse(StudioAccess.canReview(true, true, "student", true));
    assertFalse(StudioAccess.canReview(false, true, StudioAccess.STUDIO_ADMIN, false));
    assertTrue(StudioAccess.canReview(true, true, StudioAccess.STUDIO_ADMIN, false));
    assertTrue(StudioAccess.canReview(false, true, "musician", false));
    assertTrue(StudioAccess.canReview(false, true, StudioAccess.PARTNER, false));
  }
}
