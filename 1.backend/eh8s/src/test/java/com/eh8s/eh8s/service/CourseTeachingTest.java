package com.eh8s.eh8s.service;

import static com.eh8s.eh8s.service.CourseInputs.course;
import static com.eh8s.eh8s.service.CourseInputs.lesson;
import static com.eh8s.eh8s.service.CourseInputs.section;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CourseTeachingTest {

  static final String OWNER_WALLET = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";

  private final InMemoryCourseRepository repo = new InMemoryCourseRepository();
  private final CourseAuthoringService service =
      new CourseAuthoringService(
          repo,
          () -> {
            ChainConfig cfg = new ChainConfig();
            cfg.setOwnerWalletPubkey(OWNER_WALLET);
            return Optional.of(cfg);
          });
  private final CourseService student = new CourseService(repo, Optional::empty);

  CourseTeachingTest() {
    repo.account(1L, "musician", OWNER_WALLET);
    repo.account(2L, "musician", "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU");
    repo.account(3L, "musician", null);
    repo.account(4L, "musician", null);
    repo.account(5L, "musician", null);
    repo.instructors.add(3L);
    repo.instructors.add(4L);
    repo.instructors.add(5L);
    repo.levels.put(3L, 5);
    repo.levels.put(4L, 5);
    repo.levels.put(5L, 4);
  }

  private static HttpStatus status(Runnable call) {
    return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
  }

  private Course draftWithLesson(long authorId) {
    Course c = service.createCourse(authorId, course("Walking bass"));
    CourseSection s = service.createSection(authorId, c.getId(), section("Basics"));
    Lesson lesson = lesson("Roots and fifths", "https://youtu.be/dQw4w9WgXcQ");
    lesson.setPracticePrompt("Record 16 bars over a blues in F.");
    service.createLesson(authorId, s.getId(), lesson);
    return c;
  }

  @Test
  @SuppressWarnings("unchecked")
  void eligibilityListsEachRequirement() {
    Map<String, Object> ready = service.eligibility(3L);
    assertEquals(true, ready.get("canTeach"));
    Map<String, Object> almost = service.eligibility(5L);
    assertEquals(false, almost.get("canTeach"));
    List<Map<String, Object>> reqs = (List<Map<String, Object>>) almost.get("requirements");
    assertEquals(false, reqs.get(0).get("met"));
    assertEquals("Your level: 4", reqs.get(0).get("detail"));
    assertEquals(true, reqs.get(1).get("met"));
    Map<String, Object> musician = service.eligibility(2L);
    assertEquals(false, musician.get("canTeach"));
    assertEquals("No musician profile yet", ((List<Map<String, Object>>) musician.get("requirements")).get(0).get("detail"));
    assertEquals(true, service.eligibility(1L).get("canTeach"));
  }

  @Test
  void onlyLevelFiveInstructorsAuthorAndOnlyTheirOwnCourses() {
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.createCourse(5L, course("x"))));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.createCourse(2L, course("x"))));
    Course mine = draftWithLesson(3L);
    assertEquals(3L, mine.getAuthorAccountId());
    assertEquals("draft", mine.getReviewStatus());
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.updateCourse(4L, mine.getId(), course("stolen"))));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.course(4L, mine.getId())));
    draftWithLesson(4L);
    assertEquals(1, service.courses(3L).size());
    assertEquals(2, service.courses(1L).size());
  }

  @Test
  void instructorsCannotPublishArchiveReorderOrGrant() {
    Course mine = draftWithLesson(3L);
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.setStatus(3L, mine.getId(), "published")));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.setStatus(3L, mine.getId(), "archived")));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.moveCourse(3L, mine.getId(), 1)));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.grants(3L)));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.reviewQueue(3L)));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.approve(3L, mine.getId())));
  }

  @Test
  void submitFreezesRejectReopensApprovePublishes() {
    Course empty = service.createCourse(3L, course("Empty"));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.submit(3L, empty.getId())));

    Course c = draftWithLesson(3L);
    assertEquals(HttpStatus.CONFLICT, status(() -> service.approve(1L, c.getId())));
    Course submitted = service.submit(3L, c.getId());
    assertEquals("submitted", submitted.getReviewStatus());
    assertNotNull(submitted.getSubmittedAt());
    assertEquals(HttpStatus.CONFLICT, status(() -> service.updateCourse(3L, c.getId(), course("late edit"))));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.createSection(3L, c.getId(), section("late"))));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.submit(3L, c.getId())));
    assertEquals(1, service.reviewQueue(1L).size());

    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.reject(1L, c.getId(), null)));
    Course rejected = service.reject(1L, c.getId(), "Add a slower demo take.");
    assertEquals("rejected", rejected.getReviewStatus());
    assertEquals("draft", rejected.getStatus());
    assertEquals("Add a slower demo take.", rejected.getReviewNote());
    assertEquals(0, service.reviewQueue(1L).size());
    assertEquals("Slower take", service.updateCourse(3L, c.getId(), course("Slower take")).getTitle());

    Course resubmitted = service.submit(3L, c.getId());
    assertNull(resubmitted.getReviewNote());
    Course approved = service.approve(1L, c.getId());
    assertEquals("published", approved.getStatus());
    assertEquals("approved", approved.getReviewStatus());
    assertEquals(1L, approved.getReviewedByAccountId());
    assertNotNull(approved.getPublishedAt());
    assertEquals(HttpStatus.CONFLICT, status(() -> service.updateCourse(3L, c.getId(), course("live edit"))));

    Course back = service.setStatus(1L, c.getId(), "draft");
    assertEquals("draft", back.getReviewStatus());
    assertEquals("Reopened", service.updateCourse(3L, c.getId(), course("Reopened")).getTitle());
  }

  @Test
  @SuppressWarnings("unchecked")
  void lockedLessonsHidePracticePromptAndResources() {
    Course c = draftWithLesson(1L);
    service.setStatus(1L, c.getId(), "published");
    Map<String, Object> outline = student.course(2L, c.getId());
    Map<String, Object> section = ((List<Map<String, Object>>) outline.get("sections")).get(0);
    Lesson inOutline = (Lesson) ((List<Map<String, Object>>) section.get("lessons")).get(0).get("lesson");
    assertNull(inOutline.getPracticePrompt());
    Lesson locked = (Lesson) student.lesson(2L, inOutline.getId()).get("lesson");
    assertNull(locked.getPracticePrompt());
    Lesson authorView = (Lesson) student.lesson(1L, inOutline.getId()).get("lesson");
    assertEquals("Record 16 bars over a blues in F.", authorView.getPracticePrompt());
  }
}
