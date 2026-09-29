package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.LessonProgress;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CourseServiceTest {

  private final InMemoryCourseRepository repo = new InMemoryCourseRepository();
  private final IChainConfigRepository chain =
      () -> {
        ChainConfig cfg = new ChainConfig();
        cfg.setOwnerWalletPubkey(CourseAuthoringServiceTest.OWNER_WALLET);
        return Optional.of(cfg);
      };
  private final CourseAuthoringService authoring = new CourseAuthoringService(repo, chain);
  private final CourseService service = new CourseService(repo, chain);
  private final Course course;
  private final Long preview;
  private final Long paid;
  private final Long hidden;

  CourseServiceTest() {
    repo.account(1L, "musician", CourseAuthoringServiceTest.OWNER_WALLET);
    repo.account(2L, "musician", CourseAuthoringServiceTest.STUDENT_WALLET);
    repo.account(4L, "musician", "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T");
    course = authoring.createCourse(1L, CourseInputs.course("Groove", null, 2));
    CourseSection s = authoring.createSection(1L, course.getId(), CourseInputs.section("Start"));
    Lesson welcome = CourseInputs.lesson("Welcome", "https://youtu.be/dQw4w9WgXcQ");
    welcome.setIsFreePreview((byte) 1);
    preview = authoring.createLesson(1L, s.getId(), welcome).getId();
    paid = authoring.createLesson(1L, s.getId(), CourseInputs.lesson("Pocket", "https://vimeo.com/76979871")).getId();
    Lesson old = authoring.createLesson(1L, s.getId(), CourseInputs.lesson("Old take", "https://vimeo.com/5"));
    hidden = old.getId();
    authoring.updateLesson(1L, hidden, CourseInputs.withActive(old, false));
  }

  private static HttpStatus status(Runnable call) {
    return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
  }

  @SuppressWarnings("unchecked")
  private static Lesson lessonOf(Map<String, Object> body) {
    return (Lesson) body.get("lesson");
  }

  @Test
  void draftCoursesAreHiddenFromStudentsButVisibleToOwner() {
    assertEquals(0, service.catalog(2L).size());
    assertEquals(HttpStatus.NOT_FOUND, status(() -> service.course(2L, course.getId())));
    assertNotNull(service.course(1L, course.getId()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void lockedLessonHidesVideoLinks() {
    authoring.setStatus(1L, course.getId(), "published");
    List<Map<String, Object>> catalog = service.catalog(2L);
    assertEquals(1, catalog.size());
    assertEquals(2, catalog.get(0).get("lessonCount"));
    assertEquals("plan", ((Map<String, Object>) catalog.get(0).get("access")).get("lockReason"));

    Map<String, Object> locked = service.lesson(2L, paid);
    assertTrue((Boolean) locked.get("locked"));
    assertNull(lessonOf(locked).getEmbedUrl());
    assertNull(lessonOf(locked).getVideoUrl());
    assertEquals("Pocket", lessonOf(locked).getTitle());
    assertEquals(preview, locked.get("previousLessonId"));
    assertNull(locked.get("nextLessonId"));

    Map<String, Object> free = service.lesson(2L, preview);
    assertFalse((Boolean) free.get("locked"));
    assertNotNull(lessonOf(free).getEmbedUrl());

    assertEquals(HttpStatus.NOT_FOUND, status(() -> service.lesson(2L, hidden)));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.saveProgress(2L, paid, 10, false)));

    Map<String, Object> outline = service.course(2L, course.getId());
    List<Map<String, Object>> sections = (List<Map<String, Object>>) outline.get("sections");
    List<Map<String, Object>> rows = (List<Map<String, Object>>) sections.get(0).get("lessons");
    assertEquals(2, rows.size());
    rows.forEach(r -> assertNull(lessonOf(r).getEmbedUrl()));
  }

  @Test
  void planPlusLevelUnlocksAndGrantOverrides() {
    authoring.setStatus(1L, course.getId(), "published");
    repo.activePlans.add(2L);
    repo.levels.put(2L, 1);
    Map<String, Object> low = service.lesson(2L, paid);
    assertTrue((Boolean) low.get("locked"));
    assertEquals("level", low.get("lockReason"));
    repo.levels.put(2L, 2);
    assertFalse((Boolean) service.lesson(2L, paid).get("locked"));

    assertTrue((Boolean) service.lesson(4L, paid).get("locked"));
    authoring.grant(1L, CourseInputs.grant("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T", course.getId(), null));
    assertNotNull(lessonOf(service.lesson(4L, paid)).getEmbedUrl());
  }

  @Test
  void progressKeepsCompletionAndReportsPercent() {
    authoring.setStatus(1L, course.getId(), "published");
    Map<String, Object> first = service.saveProgress(2L, preview, 30, false);
    assertNull(((LessonProgress) first.get("progress")).getCompletedAt());
    assertEquals(0, first.get("percent"));
    Map<String, Object> done = service.saveProgress(2L, preview, 60, true);
    assertNotNull(((LessonProgress) done.get("progress")).getCompletedAt());
    assertEquals(50, done.get("percent"));
    Map<String, Object> rewatch = service.saveProgress(2L, preview, 5, false);
    assertNotNull(((LessonProgress) rewatch.get("progress")).getCompletedAt());
    assertEquals(0, ((LessonProgress) service.saveProgress(2L, preview, null, false).get("progress")).getLastPositionSec());
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.saveProgress(2L, preview, -1, false)));
    assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.catalog(null)));
  }
}
