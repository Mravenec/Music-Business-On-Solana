package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ICourseAuthoringController;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.service.interfaces.ICourseAuthoringService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves course authoring, course access management and instructor course review for the platform
 * owner.
 */
@RestController
@RequestMapping("/api/owner")
public class CourseAuthoringController implements ICourseAuthoringController {

  private final ICourseAuthoringService authoringService;

  /**
   * Creates the controller.
   *
   * @param authoringService authoring use cases
   */
  public CourseAuthoringController(ICourseAuthoringService authoringService) {
    this.authoringService = authoringService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/courses")
  public List<Map<String, Object>> courses(@AuthenticationPrincipal JwtPrincipal principal) {
    return authoringService.courses(accountId(principal));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/courses/{courseId}")
  public Map<String, Object> course(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long courseId) {
    return authoringService.course(accountId(principal), courseId);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses")
  public Course createCourse(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody Course input) {
    return authoringService.createCourse(accountId(principal), input);
  }

  /** {@inheritDoc} */
  @Override
  @PutMapping("/courses/{courseId}")
  public Course updateCourse(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long courseId,
      @RequestBody Course input) {
    return authoringService.updateCourse(accountId(principal), courseId, input);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/move")
  public List<Course> moveCourse(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long courseId,
      @RequestBody Course input) {
    return authoringService.moveCourse(accountId(principal), courseId, input.getSortOrder());
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/publish")
  public Course publish(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long courseId) {
    return authoringService.setStatus(accountId(principal), courseId, "published");
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/archive")
  public Course archive(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long courseId) {
    return authoringService.setStatus(accountId(principal), courseId, "archived");
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/draft")
  public Course draft(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long courseId) {
    return authoringService.setStatus(accountId(principal), courseId, "draft");
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/sections")
  public CourseSection createSection(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long courseId,
      @RequestBody CourseSection input) {
    return authoringService.createSection(accountId(principal), courseId, input);
  }

  /** {@inheritDoc} */
  @Override
  @PutMapping("/sections/{sectionId}")
  public CourseSection updateSection(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long sectionId,
      @RequestBody CourseSection input) {
    return authoringService.updateSection(accountId(principal), sectionId, input);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/sections/{sectionId}/move")
  public List<CourseSection> moveSection(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long sectionId,
      @RequestBody CourseSection input) {
    return authoringService.moveSection(accountId(principal), sectionId, input.getSortOrder());
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/sections/{sectionId}/lessons")
  public Lesson createLesson(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long sectionId,
      @RequestBody Lesson input) {
    return authoringService.createLesson(accountId(principal), sectionId, input);
  }

  /** {@inheritDoc} */
  @Override
  @PutMapping("/lessons/{lessonId}")
  public Lesson updateLesson(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long lessonId,
      @RequestBody Lesson input) {
    return authoringService.updateLesson(accountId(principal), lessonId, input);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/lessons/{lessonId}/move")
  public List<Lesson> moveLesson(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long lessonId,
      @RequestBody Lesson input) {
    return authoringService.moveLesson(accountId(principal), lessonId, input.getSortOrder());
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/course-grants")
  public List<CourseAccessGrant> grants(@AuthenticationPrincipal JwtPrincipal principal) {
    return authoringService.grants(accountId(principal));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/course-grants")
  public CourseAccessGrant grant(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody CourseAccessGrant input) {
    return authoringService.grant(accountId(principal), input);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/course-grants/{grantId}/revoke")
  public CourseAccessGrant revokeGrant(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long grantId) {
    return authoringService.revokeGrant(accountId(principal), grantId);
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/course-reviews")
  public List<Map<String, Object>> reviewQueue(@AuthenticationPrincipal JwtPrincipal principal) {
    return authoringService.reviewQueue(accountId(principal));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/approve")
  public Course approve(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long courseId) {
    return authoringService.approve(accountId(principal), courseId);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/courses/{courseId}/reject")
  public Course reject(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long courseId,
      @RequestBody(required = false) Course input) {
    return authoringService.reject(accountId(principal), courseId, input == null ? null : input.getReviewNote());
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
