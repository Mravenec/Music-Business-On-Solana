package com.eh8s.eh8s.service.course;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure academy rules shared by the student and authoring services (no I/O, easy to test). */
public final class CourseRules {

  /** Course lifecycle values stored in {@code course.status}. */
  public static final Set<String> STATUSES = Set.of("draft", "published", "archived");

  /** Highest Enigma level (0 Genesis … 5 Stage). */
  public static final int MAX_LEVEL = 5;

  /** Enigma level an instructor needs before authoring courses. */
  public static final int TEACH_LEVEL = 5;

  /** Owner review values stored in {@code course.review_status}. */
  public static final Set<String> REVIEW_STATUSES = Set.of("draft", "submitted", "approved", "rejected");

  private CourseRules() {}

  /**
   * Whether a non-owner may author courses: an approved instructor who reached the teaching level.
   *
   * @param instructor account holds an instructor profile or an approved instructor role
   * @param callerLevel caller Enigma level number, or {@code null} without a musician profile
   * @return {@code true} when both requirements are met
   */
  public static boolean canTeach(boolean instructor, Integer callerLevel) {
    return instructor && callerLevel != null && callerLevel >= TEACH_LEVEL;
  }

  /**
   * Whether an instructor may still change their own course. Submitted and published courses are
   * frozen until the owner rejects them or moves them back to draft.
   *
   * @param status {@code course.status}
   * @param reviewStatus {@code course.review_status}
   * @return {@code true} for a draft that is not waiting for review
   */
  public static boolean authorCanEdit(String status, String reviewStatus) {
    return "draft".equals(status) && ("draft".equals(reviewStatus) || "rejected".equals(reviewStatus));
  }

  /**
   * Decides whether a caller may play a lesson video.
   *
   * @param freePreview lesson is marked as a free preview
   * @param privileged caller is the platform owner or the course author
   * @param granted caller wallet holds an active, unexpired course access grant
   * @param activePlan caller holds a confirmed, unexpired academy plan
   * @param callerLevel caller Enigma level number, or {@code null} without a musician profile
   * @param minLevel minimum Enigma level the course requires
   * @return {@code true} when the video may be embedded for this caller
   */
  public static boolean canWatch(
      boolean freePreview,
      boolean privileged,
      boolean granted,
      boolean activePlan,
      Integer callerLevel,
      int minLevel) {
    if (freePreview || privileged || granted) {
      return true;
    }
    return activePlan && callerLevel != null && callerLevel >= minLevel;
  }

  /**
   * Whether an account is the platform owner: role {@code owner} or the chain config owner wallet.
   *
   * @param role account role
   * @param walletPubkey account wallet, may be {@code null}
   * @param ownerWallet chain config owner wallet, may be {@code null}
   * @return {@code true} for the owner
   */
  public static boolean isOwner(String role, String walletPubkey, String ownerWallet) {
    if ("owner".equals(role)) {
      return true;
    }
    return walletPubkey != null && ownerWallet != null && !ownerWallet.isBlank() && ownerWallet.equals(walletPubkey);
  }

  /**
   * Explains why a lesson is locked, for the student screen.
   *
   * @param activePlan caller holds a confirmed, unexpired academy plan
   * @param callerLevel caller Enigma level number, or {@code null}
   * @param minLevel minimum Enigma level the course requires
   * @return {@code "plan"} when a plan is missing, otherwise {@code "level"}
   */
  public static String lockReason(boolean activePlan, Integer callerLevel, int minLevel) {
    if (!activePlan) {
      return "plan";
    }
    return callerLevel == null || callerLevel < minLevel ? "level" : null;
  }

  /**
   * Moves one id to a 1-based position and returns the new gap-free order.
   *
   * @param ids current order
   * @param id id to move
   * @param position wanted 1-based position (clamped to 1..size)
   * @return new order; the caller stores {@code index + 1} as {@code sort_order}
   * @throws IllegalArgumentException when {@code id} is not in {@code ids}
   */
  public static List<Long> moveTo(List<Long> ids, Long id, int position) {
    List<Long> out = new ArrayList<>(ids);
    if (!out.remove(id)) {
      throw new IllegalArgumentException("id not in list");
    }
    int index = Math.max(0, Math.min(out.size(), position - 1));
    out.add(index, id);
    return out;
  }

  /**
   * Percent of lessons completed, rounded down.
   *
   * @param completed completed lesson count
   * @param total active lesson count
   * @return 0..100
   */
  public static int percent(long completed, long total) {
    if (total <= 0) {
      return 0;
    }
    return (int) Math.min(100, (completed * 100) / total);
  }
}
