package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import java.util.List;
import java.util.Map;

/**
 * Authoring side of the academy video library. Every method resolves the author from the JWT
 * account (never from the request body) and answers 403 when that account may not author.
 * Authors are the owner (every course) and eligible instructors (their own courses only).
 * Publishing, archiving, course order, access grants and review decisions stay owner-only.
 * Inputs are the generated JOOQ POJOs; ids, status, author and review columns on those inputs
 * are ignored.
 */
public interface ICourseAuthoringService {

  /**
   * Every course the caller may author, any status, with section and lesson counts.
   *
   * @param accountId caller account id from the JWT
   * @return one map per course ({@code course}, {@code sectionCount}, {@code lessonCount})
   */
  List<Map<String, Object>> courses(Long accountId);

  /**
   * Full authoring outline: every section and lesson (inactive included) with video links.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @return {@code course} and {@code sections} (each with {@code section} and {@code lessons})
   */
  Map<String, Object> course(Long accountId, Long courseId);

  /**
   * Creates a draft course.
   *
   * @param accountId caller account id from the JWT
   * @param input {@code title} (required), optional {@code summary}, {@code instrumentId},
   *     {@code minLevel} (0..5, default 0)
   * @return stored course
   */
  Course createCourse(Long accountId, Course input);

  /**
   * Replaces the editable fields of a course (the form always sends all of them): a null
   * optional field clears it, {@code title} stays required, a null {@code minLevel} means 0.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @param input {@code title}, {@code summary}, {@code instrumentId}, {@code minLevel}
   * @return updated course
   */
  Course updateCourse(Long accountId, Long courseId, Course input);

  /**
   * Moves a course to a 1-based catalog position.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @param sortOrder target position (1 or more; beyond the end moves it last)
   * @return every course in the new order
   */
  List<Course> moveCourse(Long accountId, Long courseId, Integer sortOrder);

  /**
   * Changes course status. Publishing needs at least one active lesson in an active section.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @param status {@code draft}, {@code published} or {@code archived}
   * @return updated course
   */
  Course setStatus(Long accountId, Long courseId, String status);

  /**
   * Adds a section at the end of a course.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @param input {@code title}
   * @return stored section
   */
  CourseSection createSection(Long accountId, Long courseId, CourseSection input);

  /**
   * Updates a section: {@code title} is required, {@code isActive} (0 or 1) is kept when null.
   *
   * @param accountId caller account id from the JWT
   * @param sectionId section id
   * @param input {@code title}, optional {@code isActive}
   * @return updated section
   */
  CourseSection updateSection(Long accountId, Long sectionId, CourseSection input);

  /**
   * Moves a section to a 1-based position inside its course.
   *
   * @param accountId caller account id from the JWT
   * @param sectionId section id
   * @param sortOrder target position (1 or more)
   * @return sections of that course in the new order
   */
  List<CourseSection> moveSection(Long accountId, Long sectionId, Integer sortOrder);

  /**
   * Adds a lesson at the end of a section. The video link is validated and turned into an embed
   * URL (YouTube, Vimeo, Bunny Stream, Cloudflare Stream).
   *
   * @param accountId caller account id from the JWT
   * @param sectionId section id
   * @param input {@code title}, {@code videoUrl}, optional {@code description},
   *     {@code durationSec}, {@code isFreePreview} (0 or 1), {@code resources},
   *     {@code practicePrompt}
   * @return stored lesson
   */
  Lesson createLesson(Long accountId, Long sectionId, Lesson input);

  /**
   * Replaces the editable fields of a lesson (the form always sends all of them): a null
   * optional text or duration clears it; {@code isFreePreview} and {@code isActive} are kept
   * when null.
   *
   * @param accountId caller account id from the JWT
   * @param lessonId lesson id
   * @param input the create fields plus optional {@code isActive}
   * @return updated lesson
   */
  Lesson updateLesson(Long accountId, Long lessonId, Lesson input);

  /**
   * Moves a lesson to a 1-based position inside its section.
   *
   * @param accountId caller account id from the JWT
   * @param lessonId lesson id
   * @param sortOrder target position (1 or more)
   * @return lessons of that section in the new order
   */
  List<Lesson> moveLesson(Long accountId, Long lessonId, Integer sortOrder);

  /**
   * Lists every access grant, newest first (owner only).
   *
   * @param accountId caller account id from the JWT
   * @return grants
   */
  List<CourseAccessGrant> grants(Long accountId);

  /**
   * Grants a wallet access to one course or to every course. Granting the same wallet and scope
   * again reactivates and updates the existing row.
   *
   * @param accountId caller account id from the JWT
   * @param input {@code walletPubkey}, optional {@code courseId} (null = all courses),
   *     {@code expiresAt} (future date-time, null = no expiry), {@code note}
   * @return stored grant
   */
  CourseAccessGrant grant(Long accountId, CourseAccessGrant input);

  /**
   * Revokes a grant (keeps the row for audit).
   *
   * @param accountId caller account id from the JWT
   * @param grantId grant id
   * @return revoked grant
   */
  CourseAccessGrant revokeGrant(Long accountId, Long grantId);

  /**
   * Teaching eligibility for the caller: Enigma level 5 and an approved instructor role, or the
   * owner. Each requirement is listed with met / not met so the screen can show what is missing.
   *
   * @param accountId caller account id from the JWT
   * @return {@code canTeach}, {@code owner}, {@code level}, {@code requiredLevel},
   *     {@code instructor} and {@code requirements} ({@code key}, {@code label}, {@code met},
   *     {@code detail})
   */
  Map<String, Object> eligibility(Long accountId);

  /**
   * Sends the caller's own draft course to the owner for review. The course needs at least one
   * visible lesson; after submitting, the author cannot edit it until the owner rejects it.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @return course with {@code reviewStatus = submitted}
   */
  Course submit(Long accountId, Long courseId);

  /**
   * Owner review queue: courses waiting for review, oldest submission first, with author name and
   * counts.
   *
   * @param accountId caller account id from the JWT (must be the owner)
   * @return one map per course ({@code course}, {@code authorName}, {@code sectionCount},
   *     {@code lessonCount})
   */
  List<Map<String, Object>> reviewQueue(Long accountId);

  /**
   * Owner approves a submitted course, which publishes it.
   *
   * @param accountId caller account id from the JWT (must be the owner)
   * @param courseId course id
   * @return published course with {@code reviewStatus = approved}
   */
  Course approve(Long accountId, Long courseId);

  /**
   * Owner rejects a submitted course with a note; it returns to the author as an editable draft.
   *
   * @param accountId caller account id from the JWT (must be the owner)
   * @param courseId course id
   * @param note review note stored as {@code course.review_note} (required, up to 500 characters)
   * @return course with {@code reviewStatus = rejected}
   */
  Course reject(Long accountId, Long courseId, String note);
}
