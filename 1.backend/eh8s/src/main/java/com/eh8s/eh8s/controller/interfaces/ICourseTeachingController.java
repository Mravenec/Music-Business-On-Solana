package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for the teaching path: eligibility, an instructor's own draft courses and
 * submitting them for owner review. There is no publish, archive, order or grant endpoint here.
 */
public interface ICourseTeachingController {

  /**
   * Teaching eligibility with each requirement met / not met.
   *
   * @param principal JWT principal
   * @return eligibility map
   */
  Map<String, Object> eligibility(JwtPrincipal principal);

  /**
   * The caller's own courses (the owner sees every course).
   *
   * @param principal JWT principal
   * @return course rows with counts
   */
  List<Map<String, Object>> courses(JwtPrincipal principal);

  /**
   * Authoring outline of one own course.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return course and sections with lessons
   */
  Map<String, Object> course(JwtPrincipal principal, Long courseId);

  /**
   * Creates an own draft course.
   *
   * @param principal JWT principal
   * @param input course POJO ({@code title}, {@code summary}, {@code instrumentId}, {@code minLevel})
   * @return stored course
   */
  Course createCourse(JwtPrincipal principal, Course input);

  /**
   * Updates an own draft course.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @param input course POJO with every editable field (full replacement)
   * @return updated course
   */
  Course updateCourse(JwtPrincipal principal, Long courseId, Course input);

  /**
   * Sends an own draft course to the owner for review.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return submitted course
   */
  Course submit(JwtPrincipal principal, Long courseId);

  /**
   * Adds a section to an own draft course.
   *
   * @param principal JWT principal
   * @param courseId course id
   * @param input section POJO carrying {@code title}
   * @return stored section
   */
  CourseSection createSection(JwtPrincipal principal, Long courseId, CourseSection input);

  /**
   * Renames or hides a section.
   *
   * @param principal JWT principal
   * @param sectionId section id
   * @param input section POJO carrying {@code title} and optional {@code isActive} (0/1)
   * @return updated section
   */
  CourseSection updateSection(JwtPrincipal principal, Long sectionId, CourseSection input);

  /**
   * Moves a section.
   *
   * @param principal JWT principal
   * @param sectionId section id
   * @param input section POJO carrying {@code sortOrder}
   * @return sections in the new order
   */
  List<CourseSection> moveSection(JwtPrincipal principal, Long sectionId, CourseSection input);

  /**
   * Adds a lesson from a hosted video link.
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
}
