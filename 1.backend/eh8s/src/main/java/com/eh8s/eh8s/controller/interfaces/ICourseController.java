package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for students using the academy video library.
 */
public interface ICourseController {

  /**
   * Published courses with the caller's progress.
   *
   * @param principal JWT principal
   * @return course rows
   */
  List<Map<String, Object>> catalog(JwtPrincipal principal);

  /**
   * Course outline with lock and completion flags (no video links).
   *
   * @param principal JWT principal
   * @param courseId course id
   * @return outline map
   */
  Map<String, Object> course(JwtPrincipal principal, Long courseId);

  /**
   * One lesson for the player; video links, resources and the practice prompt are hidden when
   * locked.
   *
   * @param principal JWT principal
   * @param lessonId lesson id
   * @return lesson map
   */
  Map<String, Object> lesson(JwtPrincipal principal, Long lessonId);

  /**
   * Saves playback position and optional completion.
   *
   * @param principal JWT principal
   * @param lessonId lesson id
   * @param body {@code positionSec}, optional {@code completed}
   * @return progress and course percent
   */
  Map<String, Object> saveProgress(JwtPrincipal principal, Long lessonId, Map<String, Object> body);
}
