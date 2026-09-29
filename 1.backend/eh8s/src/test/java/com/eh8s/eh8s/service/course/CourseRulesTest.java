package com.eh8s.eh8s.service.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CourseRulesTest {

  @Test
  void accessDecision() {
    assertTrue(CourseRules.canWatch(true, false, false, false, null, 5), "free preview");
    assertTrue(CourseRules.canWatch(false, true, false, false, null, 5), "owner or author");
    assertTrue(CourseRules.canWatch(false, false, true, false, null, 5), "owner grant");
    assertTrue(CourseRules.canWatch(false, false, false, true, 3, 3), "plan and level");
    assertFalse(CourseRules.canWatch(false, false, false, true, 2, 3), "level too low");
    assertFalse(CourseRules.canWatch(false, false, false, true, null, 0), "no musician profile");
    assertFalse(CourseRules.canWatch(false, false, false, false, 5, 0), "no plan");
  }

  @Test
  void lockReasonNamesTheMissingPiece() {
    assertEquals("plan", CourseRules.lockReason(false, 5, 0));
    assertEquals("level", CourseRules.lockReason(true, 1, 2));
    assertEquals("level", CourseRules.lockReason(true, null, 0));
    assertNull(CourseRules.lockReason(true, 2, 2));
  }

  @Test
  void ownerByRoleOrChainWallet() {
    assertTrue(CourseRules.isOwner("owner", null, null));
    assertTrue(CourseRules.isOwner("musician", "7QV", "7QV"));
    assertFalse(CourseRules.isOwner("musician", "abc", "7QV"));
    assertFalse(CourseRules.isOwner("musician", null, "7QV"));
    assertFalse(CourseRules.isOwner("musician", "", ""));
  }

  @Test
  void moveToIsGapFreeAndClamped() {
    List<Long> ids = List.of(10L, 20L, 30L, 40L);
    assertEquals(List.of(30L, 10L, 20L, 40L), CourseRules.moveTo(ids, 30L, 1));
    assertEquals(List.of(10L, 30L, 40L, 20L), CourseRules.moveTo(ids, 20L, 4));
    assertEquals(List.of(20L, 30L, 40L, 10L), CourseRules.moveTo(ids, 10L, 99));
    assertEquals(List.of(40L, 10L, 20L, 30L), CourseRules.moveTo(ids, 40L, 0));
    assertThrows(IllegalArgumentException.class, () -> CourseRules.moveTo(ids, 99L, 1));
  }

  @Test
  void teachingNeedsInstructorRoleAndLevelFive() {
    assertTrue(CourseRules.canTeach(true, 5));
    assertFalse(CourseRules.canTeach(true, 4));
    assertFalse(CourseRules.canTeach(true, null));
    assertFalse(CourseRules.canTeach(false, 5));
  }

  @Test
  void authorEditsOnlyDraftsNotWaitingForReview() {
    assertTrue(CourseRules.authorCanEdit("draft", "draft"));
    assertTrue(CourseRules.authorCanEdit("draft", "rejected"));
    assertFalse(CourseRules.authorCanEdit("draft", "submitted"));
    assertFalse(CourseRules.authorCanEdit("published", "approved"));
    assertFalse(CourseRules.authorCanEdit("archived", "draft"));
  }

  @Test
  void percentRoundsDown() {
    assertEquals(0, CourseRules.percent(0, 0));
    assertEquals(33, CourseRules.percent(1, 3));
    assertEquals(100, CourseRules.percent(3, 3));
  }
}
