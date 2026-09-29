package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for authoring academy courses and managing course access (owner).
 */
public interface ICourseAuthoringController {

  /**
   * Courses the caller may author, any status.
   *
   * @param principal JWT principal
   * @return course rows with counts
   */
  List<Map<String, Object>> courses(JwtPrincipal principal);

  /**
   * Full authoring outline of one course.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return outline map
   */
  Map<String, Object> course(JwtPrincipal principal, Long courseId);

  /**
   * Creates a draft course.
   *
   * @param principal JWT principal
   * @param input course POJO ({@code title}, {@code summary}, {@code instrumentId}, {@code minLevel})
   * @return stored course
   */
  Course createCourse(JwtPrincipal principal, Course input);

  /**
   * Replaces the editable course fields.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @param input course POJO with every editable field (full replacement)
   * @return updated course
   */
  Course updateCourse(JwtPrincipal principal, Long courseId, Course input);

  /**
   * Moves a course in the catalog.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @param input course POJO carrying {@code sortOrder}
   * @return courses in the new order
   */
  List<Course> moveCourse(JwtPrincipal principal, Long courseId, Course input);

  /**
   * Publishes a course.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return updated course
   */
  Course publish(JwtPrincipal principal, Long courseId);

  /**
   * Archives a course.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return updated course
   */
  Course archive(JwtPrincipal principal, Long courseId);

  /**
   * Returns a course to draft.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return updated course
   */
  Course draft(JwtPrincipal principal, Long courseId);

  /**
   * Adds a section.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @param input section POJO carrying {@code title}
   * @return stored section
   */
  CourseSection createSection(JwtPrincipal principal, Long courseId, CourseSection input);

  /**
   * Updates a section.
   *
   * @param principal JWT principal
   * @param sectionId section id
   * @param input section POJO carrying {@code title} and optional {@code isActive} (0/1)
   * @return updated section
   */
  CourseSection updateSection(JwtPrincipal principal, Long sectionId, CourseSection input);

  /**
   * Moves a section inside its course.
   *
   * @param principal JWT principal
   * @param sectionId section id
   * @param input section POJO carrying {@code sortOrder}
   * @return sections in the new order
   */
  List<CourseSection> moveSection(JwtPrincipal principal, Long sectionId, CourseSection input);

  /**
   * Adds a lesson from a video link.
   *
   * @param principal JWT principal
   * @param sectionId section id
   * @param input lesson POJO including {@code videoUrl} and optional {@code isFreePreview} (0/1)
   * @return stored lesson
   */
  Lesson createLesson(JwtPrincipal principal, Long sectionId, Lesson input);

  /**
   * Updates a lesson.
   *
   * @param principal JWT principal
   * @param lessonId lesson id
   * @param input lesson POJO with every editable field plus optional {@code isActive} (0/1)
   * @return updated lesson
   */
  Lesson updateLesson(JwtPrincipal principal, Long lessonId, Lesson input);

  /**
   * Moves a lesson inside its section.
   *
   * @param principal JWT principal
   * @param lessonId lesson id
   * @param input lesson POJO carrying {@code sortOrder}
   * @return lessons in the new order
   */
  List<Lesson> moveLesson(JwtPrincipal principal, Long lessonId, Lesson input);

  /**
   * Lists course access grants.
   *
   * @param principal JWT principal
   * @return grants newest first
   */
  List<CourseAccessGrant> grants(JwtPrincipal principal);

  /**
   * Grants a wallet access to one course or all courses.
   *
   * @param principal JWT principal
   * @param input grant POJO: {@code walletPubkey}, optional {@code courseId}, {@code expiresAt} (date-time), {@code note}
   * @return stored grant
   */
  CourseAccessGrant grant(JwtPrincipal principal, CourseAccessGrant input);

  /**
   * Revokes a grant.
   *
   * @param principal JWT principal
   * @param grantId grant id
   * @return revoked grant
   */
  CourseAccessGrant revokeGrant(JwtPrincipal principal, Long grantId);

  /**
   * Instructor courses waiting for owner review, oldest submission first.
   *
   * @param principal JWT principal (owner)
   * @return course rows with author name and counts
   */
  List<Map<String, Object>> reviewQueue(JwtPrincipal principal);

  /**
   * Approves a submitted course, which publishes it.
   *
   * @param principal JWT principal (owner)
   * @param courseId course id
   * @return published course
   */
  Course approve(JwtPrincipal principal, Long courseId);

  /**
   * Rejects a submitted course with a note; it returns to the author as a draft.
   *
   * @param principal JWT principal (owner)
   * @param courseId course id
   * @param input course POJO carrying {@code reviewNote}
   * @return rejected course
   */
  Course reject(JwtPrincipal principal, Long courseId, Course input);
}
