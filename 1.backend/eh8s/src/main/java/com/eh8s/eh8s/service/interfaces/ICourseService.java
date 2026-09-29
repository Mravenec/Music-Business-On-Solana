package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Student side of the academy video library: browse published courses, open an outline, play a
 * lesson when access allows it, and save progress.
 */
public interface ICourseService {

  /**
   * Published courses with lesson count and the caller's completion percent.
   *
   * @param accountId caller account id from the JWT
   * @return one map per course ({@code course}, {@code lessonCount}, {@code percent})
   */
  List<Map<String, Object>> catalog(Long accountId);

  /**
   * Course outline: active sections and lessons with lock and completion flags. Video links,
   * resources and practice prompts are never included here.
   *
   * @param accountId caller account id from the JWT
   * @param courseId course id
   * @return {@code course}, {@code sections}, {@code percent}, {@code access}
   */
  Map<String, Object> course(Long accountId, Long courseId);

  /**
   * One lesson for the player. {@code lesson.embedUrl}, {@code lesson.videoUrl},
   * {@code lesson.resources} and {@code lesson.practicePrompt} are {@code null} when the caller may
   * not watch it.
   *
   * @param accountId caller account id from the JWT
   * @param lessonId lesson id
   * @return {@code lesson}, {@code locked}, {@code lockReason}, {@code course}, {@code section},
   *     {@code progress}, {@code previousLessonId}, {@code nextLessonId}
   */
  Map<String, Object> lesson(Long accountId, Long lessonId);

  /**
   * Saves the last playback position and, optionally, marks the lesson completed.
   *
   * @param accountId caller account id from the JWT
   * @param lessonId lesson id
   * @param positionSec last playback position in seconds (0..86400, null = 0)
   * @param completed {@code true} marks the lesson completed (a completion is never undone)
   * @return {@code progress} and the new course {@code percent}
   */
  Map<String, Object> saveProgress(Long accountId, Long lessonId, Integer positionSec, boolean completed);
}
