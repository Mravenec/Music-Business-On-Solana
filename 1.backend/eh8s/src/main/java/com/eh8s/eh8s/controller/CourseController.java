package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ICourseController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.service.interfaces.ICourseService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the student side of the academy video library.
 */
@RestController
@RequestMapping("/api")
public class CourseController implements ICourseController {

  private final ICourseService courseService;

  /**
   * Creates the controller.
   *
   * @param courseService student course use cases
   */
  public CourseController(ICourseService courseService) {
    this.courseService = courseService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/courses")
  public List<Map<String, Object>> catalog(@AuthenticationPrincipal JwtPrincipal principal) {
    return courseService.catalog(accountId(principal));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/courses/{courseId}")
  public Map<String, Object> course(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long courseId) {
    return courseService.course(accountId(principal), courseId);
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/lessons/{lessonId}")
  public Map<String, Object> lesson(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long lessonId) {
    return courseService.lesson(accountId(principal), lessonId);
  }

  /** {@inheritDoc} */
  @Override
  @PutMapping("/lessons/{lessonId}/progress")
  public Map<String, Object> saveProgress(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long lessonId,
      @RequestBody Map<String, Object> body) {
    return courseService.saveProgress(
        accountId(principal),
        lessonId,
        HttpBody.intValue(body, "positionSec"),
        Boolean.TRUE.equals(HttpBody.bool(body, "completed")));
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
