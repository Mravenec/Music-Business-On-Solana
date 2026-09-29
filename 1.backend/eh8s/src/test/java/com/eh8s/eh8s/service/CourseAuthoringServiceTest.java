package com.eh8s.eh8s.service;

import static com.eh8s.eh8s.service.CourseInputs.course;
import static com.eh8s.eh8s.service.CourseInputs.grant;
import static com.eh8s.eh8s.service.CourseInputs.grantUntil;
import static com.eh8s.eh8s.service.CourseInputs.lesson;
import static com.eh8s.eh8s.service.CourseInputs.section;
import static com.eh8s.eh8s.service.CourseInputs.withActive;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CourseAuthoringServiceTest {

  static final String OWNER_WALLET = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String STUDENT_WALLET = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";

  private final InMemoryCourseRepository repo = new InMemoryCourseRepository();
  private final CourseAuthoringService service =
      new CourseAuthoringService(
          repo,
          () -> {
            ChainConfig cfg = new ChainConfig();
            cfg.setOwnerWalletPubkey(OWNER_WALLET);
            return Optional.of(cfg);
          });

  CourseAuthoringServiceTest() {
    repo.account(1L, "musician", OWNER_WALLET);
    repo.account(2L, "musician", STUDENT_WALLET);
    repo.account(3L, "owner", null);
  }

  private static HttpStatus status(Runnable call) {
    return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
  }

  @Test
  void ownerWalletOrOwnerRoleMayAuthorOthersMayNot() {
    assertNotNull(service.createCourse(1L, course("Groove basics")).getId());
    assertNotNull(service.createCourse(3L, course("Reading charts")).getId());
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.createCourse(2L, course("x"))));
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.grants(2L)));
    assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.courses(null)));
  }

  @Test
  void validatesCourseFields() {
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createCourse(1L, new Course())));
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createCourse(1L, course("x", null, 6))));
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createCourse(1L, course("x", 9L, null))));
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.createCourse(1L, course("t".repeat(161)))));
    Course c = service.createCourse(1L, course("Bass", 1L, 2));
    assertEquals("draft", c.getStatus());
    assertEquals((byte) 2, c.getMinLevel());
    assertEquals(1L, c.getAuthorAccountId());
    Course edit = new Course(c);
    edit.setSummary("Lines and groove");
    Course updated = service.updateCourse(1L, c.getId(), edit);
    assertEquals("Bass", updated.getTitle());
    assertEquals("Lines and groove", updated.getSummary());
    assertEquals((byte) 2, updated.getMinLevel());
    Course cleared = service.updateCourse(1L, c.getId(), course("Bass"));
    assertNull(cleared.getSummary());
    assertNull(cleared.getInstrumentId());
    assertEquals((byte) 0, cleared.getMinLevel());
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.updateCourse(1L, c.getId(), new Course())));
  }

  @Test
  void publishNeedsAnActiveLesson() {
    Course c = service.createCourse(1L, course("Drums"));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.setStatus(1L, c.getId(), "published")));
    CourseSection s = service.createSection(1L, c.getId(), section("Warm-up"));
    Lesson input = lesson("Grip", "https://youtu.be/dQw4w9WgXcQ");
    input.setDurationSec(300);
    Lesson l = service.createLesson(1L, s.getId(), input);
    assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", l.getEmbedUrl());
    assertEquals((byte) 0, l.getIsFreePreview());
    assertEquals((byte) 1, l.getIsActive());
    service.updateLesson(1L, l.getId(), withActive(l, false));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.setStatus(1L, c.getId(), "published")));
    service.updateLesson(1L, l.getId(), withActive(l, true));
    Course published = service.setStatus(1L, c.getId(), "published");
    assertEquals("published", published.getStatus());
    assertNotNull(published.getPublishedAt());
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.setStatus(1L, c.getId(), "live")));
    Lesson badFlag = withActive(l, true);
    badFlag.setIsFreePreview((byte) 2);
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.updateLesson(1L, l.getId(), badFlag)));
  }

  @Test
  void rejectsUnsupportedVideoHost() {
    Course c = service.createCourse(1L, course("Keys"));
    CourseSection s = service.createSection(1L, c.getId(), section("One"));
    assertEquals(
        HttpStatus.BAD_REQUEST,
        status(() -> service.createLesson(1L, s.getId(), lesson("x", "https://evil.io/v.mp4"))));
  }

  @Test
  void moveKeepsOrderGapFree() {
    Course c = service.createCourse(1L, course("Guitar"));
    CourseSection s = service.createSection(1L, c.getId(), section("Chords"));
    Long a = service.createLesson(1L, s.getId(), lesson("A", "https://vimeo.com/1")).getId();
    Long b = service.createLesson(1L, s.getId(), lesson("B", "https://vimeo.com/2")).getId();
    Long d = service.createLesson(1L, s.getId(), lesson("C", "https://vimeo.com/3")).getId();
    List<Lesson> order = service.moveLesson(1L, d, 1);
    assertEquals(List.of(d, a, b), order.stream().map(Lesson::getId).toList());
    assertEquals(List.of(1, 2, 3), order.stream().map(Lesson::getSortOrder).toList());
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.moveLesson(1L, a, 0)));
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.moveLesson(1L, a, null)));
  }

  @Test
  void grantIsUpsertedByWalletAndScopeAndRevocable() {
    Course c = service.createCourse(1L, course("Voice"));
    CourseAccessGrant all = service.grant(1L, grant(STUDENT_WALLET, null, "scholarship"));
    assertNull(all.getCourseId());
    CourseAccessGrant again = service.grant(1L, grant(STUDENT_WALLET, null, "renewed"));
    assertEquals(all.getId(), again.getId());
    assertEquals("renewed", again.getNote());
    CourseAccessGrant one = service.grant(1L, grant(STUDENT_WALLET, c.getId(), null));
    assertEquals(2, service.grants(1L).size());
    assertEquals((byte) 0, service.revokeGrant(1L, one.getId()).getIsActive());
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.grant(1L, grant("not-a-wallet!", null, null))));
    assertEquals(
        HttpStatus.BAD_REQUEST,
        status(() -> service.grant(1L, grantUntil(STUDENT_WALLET, LocalDateTime.of(2001, 1, 1, 23, 59, 59)))));
    assertEquals(
        HttpStatus.BAD_REQUEST, status(() -> service.grant(1L, grant(STUDENT_WALLET, 999L, null))));
  }
}
