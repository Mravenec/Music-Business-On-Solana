package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.LessonProgress;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ persistence for the academy video library: courses, sections, lessons, progress and
 * owner-issued access grants.
 */
public interface ICourseRepository {

  /**
   * Loads an account.
   *
   * @param accountId account id
   * @return account when present
   */
  Optional<Account> findAccount(Long accountId);

  /**
   * Enigma level number (0..5) of the caller's musician profile.
   *
   * @param accountId account id
   * @return level number, empty without a profile or level
   */
  Optional<Integer> findEnigmaLevel(Long accountId);

  /**
   * Whether the caller holds a confirmed academy plan that has not expired yet.
   *
   * @param accountId account id
   * @param now reference time
   * @return {@code true} with an active plan
   */
  boolean hasActivePlan(Long accountId, LocalDateTime now);

  /**
   * Whether a wallet holds an active, unexpired grant for one course or for every course.
   *
   * @param walletPubkey wallet public key
   * @param courseId course id
   * @param now reference time
   * @return {@code true} when granted
   */
  boolean hasActiveGrant(String walletPubkey, Long courseId, LocalDateTime now);

  /**
   * Whether an account is an approved instructor: it has an instructor profile, an approved
   * {@code instructor} role membership, or {@code account.role = 'instructor'}.
   *
   * @param accountId account id
   * @return {@code true} for an instructor
   */
  boolean isInstructor(Long accountId);

  /**
   * Whether an instrument id exists.
   *
   * @param instrumentId instrument id
   * @return {@code true} when present
   */
  boolean instrumentExists(Long instrumentId);

  /**
   * Lists courses in catalog order.
   *
   * @param statuses statuses to include
   * @return courses ordered by sort order, then id
   */
  List<Course> findCourses(Collection<String> statuses);

  /**
   * Loads a course.
   *
   * @param courseId course id
   * @return course when present
   */
  Optional<Course> findCourse(Long courseId);

  /**
   * Inserts a course.
   *
   * @param course course without id
   * @return stored course with id
   */
  Course insertCourse(Course course);

  /**
   * Updates every editable column of a course.
   *
   * @param course course with id
   */
  void updateCourse(Course course);

  /**
   * Lists the sections of a course in order.
   *
   * @param courseId course id
   * @return sections ordered by sort order, then id
   */
  List<CourseSection> findSections(Long courseId);

  /**
   * Loads a section.
   *
   * @param sectionId section id
   * @return section when present
   */
  Optional<CourseSection> findSection(Long sectionId);

  /**
   * Inserts a section.
   *
   * @param section section without id
   * @return stored section with id
   */
  CourseSection insertSection(CourseSection section);

  /**
   * Updates title, active flag and sort order of a section.
   *
   * @param section section with id
   */
  void updateSection(CourseSection section);

  /**
   * Rewrites section sort orders as 1..n in one transaction.
   *
   * @param orderedIds section ids in their new order
   */
  void reorderSections(List<Long> orderedIds);

  /**
   * Lists every lesson of a course in outline order (section order, then lesson order).
   *
   * @param courseId course id
   * @return lessons of all sections
   */
  List<Lesson> findLessonsByCourse(Long courseId);

  /**
   * Lists the lessons of one section in order.
   *
   * @param sectionId section id
   * @return lessons ordered by sort order, then id
   */
  List<Lesson> findLessonsBySection(Long sectionId);

  /**
   * Loads a lesson.
   *
   * @param lessonId lesson id
   * @return lesson when present
   */
  Optional<Lesson> findLesson(Long lessonId);

  /**
   * Inserts a lesson.
   *
   * @param lesson lesson without id
   * @return stored lesson with id
   */
  Lesson insertLesson(Lesson lesson);

  /**
   * Updates every editable column of a lesson.
   *
   * @param lesson lesson with id
   */
  void updateLesson(Lesson lesson);

  /**
   * Rewrites lesson sort orders as 1..n in one transaction.
   *
   * @param orderedIds lesson ids in their new order
   */
  void reorderLessons(List<Long> orderedIds);

  /**
   * Progress rows of one account for a set of lessons.
   *
   * @param accountId account id
   * @param lessonIds lesson ids
   * @return progress rows (missing lessons have no row)
   */
  List<LessonProgress> findProgress(Long accountId, Collection<Long> lessonIds);

  /**
   * Inserts or updates the progress row keyed by account and lesson.
   *
   * @param progress progress with account id, lesson id, position and optional completion time
   * @return stored progress row
   */
  LessonProgress upsertProgress(LessonProgress progress);

  /**
   * Lists every access grant, newest first.
   *
   * @return grants
   */
  List<CourseAccessGrant> findGrants();

  /**
   * Loads a grant.
   *
   * @param grantId grant id
   * @return grant when present
   */
  Optional<CourseAccessGrant> findGrant(Long grantId);

  /**
   * Loads the grant for a wallet and scope ({@code 0} = every course).
   *
   * @param walletPubkey wallet public key
   * @param courseScope course id or {@code 0}
   * @return grant when present
   */
  Optional<CourseAccessGrant> findGrantByScope(String walletPubkey, Long courseScope);

  /**
   * Inserts a grant.
   *
   * @param grant grant without id
   * @return stored grant with id
   */
  CourseAccessGrant insertGrant(CourseAccessGrant grant);

  /**
   * Updates note, expiry, active flag and granter of a grant.
   *
   * @param grant grant with id
   */
  void updateGrant(CourseAccessGrant grant);
}
