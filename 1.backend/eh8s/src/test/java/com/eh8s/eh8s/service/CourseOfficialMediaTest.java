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
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.service.course.MediaLinks;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CourseOfficialMediaTest {

  private static final String SECRET = "test-media-secret-0123456789abcdef";
  private static final String BASE = "https://media.test.invalid";
  private static final String HASH = "a".repeat(64);
  private static final String REF = "official/lesson-1.enc";
  private static final String KEY = "k-2026-10";

  private final InMemoryCourseRepository repo = new InMemoryCourseRepository();
  private final IChainConfigRepository chain =
      () -> {
        ChainConfig cfg = new ChainConfig();
        cfg.setOwnerWalletPubkey(CourseAuthoringServiceTest.OWNER_WALLET);
        return Optional.of(cfg);
      };
  private final CourseAuthoringService authoring = new CourseAuthoringService(repo, chain);
  private final CourseService service = new CourseService(repo, chain, SECRET, BASE, 120);
  private final Course course;
  private final Long official;
  private final Long preview;
  private final Long legacy;

  CourseOfficialMediaTest() {
    repo.account(1L, "musician", CourseAuthoringServiceTest.OWNER_WALLET);
    repo.account(2L, "musician", CourseAuthoringServiceTest.STUDENT_WALLET);
    course = authoring.createCourse(1L, CourseInputs.course("Official", null, 2));
    CourseSection s = authoring.createSection(1L, course.getId(), CourseInputs.section("Start"));
    Lesson free = CourseInputs.lesson("Welcome", "https://youtu.be/dQw4w9WgXcQ");
    free.setIsFreePreview((byte) 1);
    preview = authoring.createLesson(1L, s.getId(), free).getId();
    Lesson paid = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    pin(paid, HASH, REF, KEY);
    official = authoring.createLesson(1L, s.getId(), paid).getId();
    legacy = authoring.createLesson(1L, s.getId(), CourseInputs.lesson("Legacy link", "https://vimeo.com/5")).getId();
    authoring.setStatus(1L, course.getId(), "published");
  }

  private static void pin(Lesson lesson, String hash, String ref, String key) {
    lesson.setContentHash(hash);
    lesson.setStorageRef(ref);
    lesson.setKeyId(key);
  }

  private static HttpStatus status(Runnable call) {
    return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
  }

  @Test
  void ownerPinsAndNormalizesHashAndInstructorCannot() {
    Lesson stored = repo.findLesson(official).orElseThrow();
    assertEquals(HASH, stored.getContentHash());
    assertEquals(REF, stored.getStorageRef());
    assertEquals(KEY, stored.getKeyId());

    Lesson upper = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    pin(upper, "A".repeat(64), REF, KEY);
    assertEquals(HASH, authoring.updateLesson(1L, official, upper).getContentHash());

    Lesson untouched = CourseInputs.lesson("Renamed", "https://vimeo.com/76979871");
    assertEquals(REF, authoring.updateLesson(1L, official, untouched).getStorageRef());

    Lesson partial = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    partial.setContentHash(HASH);
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> authoring.updateLesson(1L, official, partial)));

    Lesson badRef = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    pin(badRef, HASH, "https://evil.example/x", KEY);
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> authoring.updateLesson(1L, official, badRef)));
    Lesson traversal = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    pin(traversal, HASH, "a/../b", KEY);
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> authoring.updateLesson(1L, official, traversal)));
    Lesson shortHash = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    pin(shortHash, "abc", REF, KEY);
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> authoring.updateLesson(1L, official, shortHash)));

    Lesson clear = CourseInputs.lesson("Official pocket", "https://vimeo.com/76979871");
    pin(clear, "", "", "");
    Lesson cleared = authoring.updateLesson(1L, legacy, clear);
    assertNull(cleared.getStorageRef());
  }

  @Test
  void nonOwnerCannotPinOfficialMedia() {
    repo.account(5L, "musician", "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T");
    repo.instructors.add(5L);
    repo.levels.put(5L, 5);
    Course own = authoring.createCourse(5L, CourseInputs.course("Mine", null, 0));
    CourseSection sec = authoring.createSection(5L, own.getId(), CourseInputs.section("S"));
    Lesson attempt = CourseInputs.lesson("Sneaky", "https://vimeo.com/5");
    pin(attempt, HASH, REF, KEY);
    assertEquals(HttpStatus.FORBIDDEN, status(() -> authoring.createLesson(5L, sec.getId(), attempt)));
  }

  @Test
  void officialPaidLessonNeverReturnsPermanentEmbedAndLockedGets403() {
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.watch(2L, official)));

    Map<String, Object> locked = service.lesson(2L, official);
    assertTrue((Boolean) locked.get("locked"));
    assertTrue((Boolean) locked.get("official"));
    assertNoPermanentLink(lessonOf(locked));

    repo.activePlans.add(2L);
    repo.levels.put(2L, 2);
    Map<String, Object> open = service.lesson(2L, official);
    assertFalse((Boolean) open.get("locked"));
    assertNoPermanentLink(lessonOf(open));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows =
        (List<Map<String, Object>>)
            ((List<Map<String, Object>>) service.course(2L, course.getId()).get("sections")).get(0).get("lessons");
    rows.forEach(r -> assertNoPermanentLink(lessonOf(r)));
  }

  @Test
  void signedUrlIsShortLivedBoundToViewerAndVerifiable() {
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.watch(2L, official)));
    repo.activePlans.add(2L);
    repo.levels.put(2L, 1);
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.watch(2L, official)));
    repo.levels.put(2L, 2);

    long before = System.currentTimeMillis() / 1000;
    Map<String, Object> watch = service.watch(2L, official);
    assertEquals("signed", watch.get("kind"));
    assertEquals(HASH, watch.get("contentHash"));
    assertEquals(KEY, watch.get("keyId"));
    assertNotNull(watch.get("expiresAt"));

    URI uri = URI.create((String) watch.get("url"));
    assertEquals("media.test.invalid", uri.getHost());
    assertEquals("/" + REF, uri.getPath());
    Map<String, String> q = query(uri);
    long exp = Long.parseLong(q.get("exp"));
    assertTrue(exp >= before + 119 && exp <= before + 125, "ttl is 120s");
    assertEquals("2", q.get("sub"));
    assertTrue(MediaLinks.verify(SECRET, REF, KEY, 2L, exp, q.get("sig"), before));
    assertFalse(MediaLinks.verify(SECRET, REF, KEY, 2L, exp, q.get("sig"), exp), "expired");
    assertFalse(MediaLinks.verify(SECRET, REF, KEY, 3L, exp, q.get("sig"), before), "other viewer");
    assertFalse(MediaLinks.verify("other-secret-0123456789abcdef-xx", REF, KEY, 2L, exp, q.get("sig"), before));
    assertFalse(MediaLinks.verify(SECRET, "official/other.enc", KEY, 2L, exp, q.get("sig"), before));
    assertFalse(MediaLinks.verify(SECRET, REF, KEY, 2L, exp + 600, q.get("sig"), before), "extended expiry");
    assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.watch(null, official)));
  }

  @Test
  void ttlIsClamped() {
    assertEquals(MediaLinks.MIN_TTL_SECONDS, MediaLinks.clampTtl(1));
    assertEquals(MediaLinks.MAX_TTL_SECONDS, MediaLinks.clampTtl(86_400));
    assertEquals(300, MediaLinks.clampTtl(300));
  }

  @Test
  void freePreviewYoutubePathAndLegacyLinksKeepWorking() {
    Map<String, Object> free = service.watch(2L, preview);
    assertEquals("embed", free.get("kind"));
    assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", free.get("url"));
    assertNull(free.get("expiresAt"));

    Lesson shown = lessonOf(service.lesson(2L, preview));
    assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", shown.getEmbedUrl());
    assertFalse((Boolean) service.lesson(2L, preview).get("official"));

    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.watch(2L, legacy)));
    repo.activePlans.add(2L);
    repo.levels.put(2L, 2);
    assertEquals("embed", service.watch(2L, legacy).get("kind"));
    assertNotNull(lessonOf(service.lesson(2L, legacy)).getEmbedUrl());
  }

  @Test
  void officialFreePreviewIsSignedNotEmbedded() {
    Lesson upd = CourseInputs.lesson("Welcome", "https://youtu.be/dQw4w9WgXcQ");
    pin(upd, HASH, "official/welcome.enc", KEY);
    authoring.updateLesson(1L, preview, upd);
    Map<String, Object> watch = service.watch(2L, preview);
    assertEquals("signed", watch.get("kind"));
    assertNoPermanentLink(lessonOf(service.lesson(2L, preview)));
  }

  @Test
  void halfPinnedLessonFailsClosed() {
    Lesson half = repo.findLesson(official).orElseThrow();
    half.setKeyId(null);
    repo.updateLesson(half);
    repo.activePlans.add(2L);
    repo.levels.put(2L, 2);
    assertEquals(HttpStatus.CONFLICT, status(() -> service.watch(2L, official)));
    assertNoPermanentLink(lessonOf(service.lesson(2L, official)));
  }

  private static void assertNoPermanentLink(Lesson lesson) {
    assertNull(lesson.getEmbedUrl());
    assertNull(lesson.getVideoUrl());
    assertNull(lesson.getStorageRef());
    assertNull(lesson.getKeyId());
    assertNull(lesson.getContentHash());
  }

  private static Lesson lessonOf(Map<String, Object> body) {
    return (Lesson) body.get("lesson");
  }

  private static Map<String, String> query(URI uri) {
    Map<String, String> out = new java.util.HashMap<>();
    for (String pair : uri.getRawQuery().split("&")) {
      String[] kv = pair.split("=", 2);
      out.put(kv[0], kv[1]);
    }
    return out;
  }
}
