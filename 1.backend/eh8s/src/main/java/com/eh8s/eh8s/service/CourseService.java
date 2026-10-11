package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.LessonProgress;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.ICourseRepository;
import com.eh8s.eh8s.service.course.CourseRules;
import com.eh8s.eh8s.service.course.MediaLinks;
import com.eh8s.eh8s.service.interfaces.ICourseService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Student side of the academy video library.
 */
@Service
public class CourseService implements ICourseService {

  private static final int MAX_POSITION_SEC = 24 * 3600;
  private static final String DEV_MEDIA_SECRET = "eh8s-dev-media-secret-change-me-32b";
  private static final String DEV_MEDIA_BASE_URL = "https://media.eh8s.invalid";

  private final ICourseRepository courseRepository;
  private final IChainConfigRepository chainConfigRepository;
  private final String mediaSecret;
  private final String mediaBaseUrl;
  private final long mediaTtlSeconds;

  /**
   * Creates the service with the default (dev) media signing settings. Used by unit tests.
   *
   * @param courseRepository course persistence
   * @param chainConfigRepository chain config (owner wallet)
   */
  public CourseService(ICourseRepository courseRepository, IChainConfigRepository chainConfigRepository) {
    this(courseRepository, chainConfigRepository, DEV_MEDIA_SECRET, DEV_MEDIA_BASE_URL, 300);
  }

  /**
   * Creates the service.
   *
   * @param courseRepository course persistence
   * @param chainConfigRepository chain config (owner wallet)
   * @param mediaSecret HMAC secret shared with the media host ({@code eh8s.media.signing-secret})
   * @param mediaBaseUrl media host base URL ({@code eh8s.media.base-url})
   * @param mediaTtlSeconds signed link lifetime, clamped to 30..900 seconds
   */
  @Autowired
  public CourseService(
      ICourseRepository courseRepository,
      IChainConfigRepository chainConfigRepository,
      @Value("${eh8s.media.signing-secret}") String mediaSecret,
      @Value("${eh8s.media.base-url}") String mediaBaseUrl,
      @Value("${eh8s.media.url-ttl-seconds:300}") long mediaTtlSeconds) {
    this.courseRepository = courseRepository;
    this.chainConfigRepository = chainConfigRepository;
    this.mediaSecret = mediaSecret;
    this.mediaBaseUrl = mediaBaseUrl;
    this.mediaTtlSeconds = MediaLinks.clampTtl(mediaTtlSeconds);
  }
  /** {@inheritDoc} */
  @Override
  public List<Map<String, Object>> catalog(Long accountId) {
    Caller caller = caller(accountId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Course course : courseRepository.findCourses(Set.of("published"))) {
      List<Lesson> lessons = visibleLessons(course.getId());
      Set<Long> done = completedIds(caller, lessons);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("course", course);
      row.put("lessonCount", lessons.size());
      row.put("totalDurationSec", lessons.stream().mapToLong(l -> nz(l.getDurationSec())).sum());
      row.put("percent", CourseRules.percent(done.size(), lessons.size()));
      row.put("access", access(caller, course));
      out.add(row);
    }
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> course(Long accountId, Long courseId) {
    Caller caller = caller(accountId);
    Course course = requireVisibleCourse(caller, courseId);
    List<CourseSection> sections = activeSections(course.getId());
    List<Lesson> lessons = visibleLessons(course.getId(), sections);
    Set<Long> done = completedIds(caller, lessons);
    boolean watchAll = watch(caller, course, false);

    List<Map<String, Object>> sectionRows = new ArrayList<>();
    for (CourseSection section : sections) {
      List<Map<String, Object>> lessonRows = new ArrayList<>();
      for (Lesson lesson : lessons) {
        if (!lesson.getSectionId().equals(section.getId())) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lesson", withoutPaidContent(lesson));
        row.put("locked", !(watchAll || flag(lesson.getIsFreePreview())));
        row.put("completed", done.contains(lesson.getId()));
        lessonRows.add(row);
      }
      Map<String, Object> sectionRow = new LinkedHashMap<>();
      sectionRow.put("section", section);
      sectionRow.put("lessons", lessonRows);
      sectionRows.add(sectionRow);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("course", course);
    out.put("sections", sectionRows);
    out.put("lessonCount", lessons.size());
    out.put("percent", CourseRules.percent(done.size(), lessons.size()));
    out.put("access", access(caller, course));
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> lesson(Long accountId, Long lessonId) {
    Caller caller = caller(accountId);
    Lesson lesson = courseRepository.findLesson(lessonId).orElseThrow(CourseService::lessonNotFound);
    CourseSection section =
        courseRepository.findSection(lesson.getSectionId()).orElseThrow(CourseService::lessonNotFound);
    Course course = requireVisibleCourse(caller, section.getCourseId());
    List<Lesson> ordered = visibleLessons(course.getId());
    int index = indexOf(ordered, lesson.getId());
    if (index < 0) {
      throw lessonNotFound();
    }
    boolean canWatch = watch(caller, course, flag(lesson.getIsFreePreview()));
    LessonProgress progress =
        courseRepository.findProgress(caller.account().getId(), List.of(lesson.getId())).stream()
            .findFirst()
            .orElse(null);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("lesson", canWatch ? withoutOfficialMedia(lesson) : withoutPaidContent(lesson));
    out.put("official", MediaLinks.isOfficial(lesson));
    out.put("locked", !canWatch);
    out.put("lockReason", canWatch ? null : CourseRules.lockReason(caller.activePlan(), caller.level(), minLevel(course)));
    out.put("course", course);
    out.put("section", section);
    out.put("progress", progress);
    out.put("previousLessonId", index > 0 ? ordered.get(index - 1).getId() : null);
    out.put("nextLessonId", index < ordered.size() - 1 ? ordered.get(index + 1).getId() : null);
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> watch(Long accountId, Long lessonId) {
    Caller caller = caller(accountId);
    Lesson lesson = courseRepository.findLesson(lessonId).orElseThrow(CourseService::lessonNotFound);
    CourseSection section =
        courseRepository.findSection(lesson.getSectionId()).orElseThrow(CourseService::lessonNotFound);
    Course course = requireVisibleCourse(caller, section.getCourseId());
    if (indexOf(visibleLessons(course.getId()), lesson.getId()) < 0) {
      throw lessonNotFound();
    }
    if (!watch(caller, course, flag(lesson.getIsFreePreview()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This lesson is locked for your account");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("lessonId", lesson.getId());
    if (!MediaLinks.isOfficial(lesson)) {
      out.put("kind", "embed");
      out.put("url", lesson.getEmbedUrl());
      out.put("expiresAt", null);
      out.put("contentHash", null);
      out.put("keyId", null);
      return out;
    }
    if (!MediaLinks.isFullyPinned(lesson)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Official media is not fully pinned yet");
    }
    long expiresAt = Instant.now().getEpochSecond() + mediaTtlSeconds;
    out.put("kind", "signed");
    out.put(
        "url",
        MediaLinks.signedUrl(
            mediaBaseUrl,
            mediaSecret,
            lesson.getStorageRef(),
            lesson.getKeyId(),
            caller.account().getId(),
            expiresAt));
    out.put("expiresAt", Instant.ofEpochSecond(expiresAt).toString());
    out.put("contentHash", lesson.getContentHash());
    out.put("keyId", lesson.getKeyId());
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> saveProgress(Long accountId, Long lessonId, Integer positionSec, boolean completed) {
    Caller caller = caller(accountId);
    Lesson lesson = courseRepository.findLesson(lessonId).orElseThrow(CourseService::lessonNotFound);
    CourseSection section =
        courseRepository.findSection(lesson.getSectionId()).orElseThrow(CourseService::lessonNotFound);
    Course course = requireVisibleCourse(caller, section.getCourseId());
    List<Lesson> ordered = visibleLessons(course.getId());
    if (indexOf(ordered, lesson.getId()) < 0) {
      throw lessonNotFound();
    }
    if (!watch(caller, course, flag(lesson.getIsFreePreview()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This lesson is locked for your account");
    }
    int position = position(positionSec);

    LessonProgress existing =
        courseRepository.findProgress(caller.account().getId(), List.of(lesson.getId())).stream()
            .findFirst()
            .orElse(null);
    LessonProgress row = new LessonProgress();
    row.setAccountId(caller.account().getId());
    row.setLessonId(lesson.getId());
    row.setLastPositionSec(position);
    LocalDateTime completedAt = existing == null ? null : existing.getCompletedAt();
    if (completed && completedAt == null) {
      completedAt = LocalDateTime.now();
    }
    row.setCompletedAt(completedAt);
    LessonProgress stored = courseRepository.upsertProgress(row);

    Set<Long> done = completedIds(caller, ordered);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("progress", stored);
    out.put("percent", CourseRules.percent(done.size(), ordered.size()));
    return out;
  }

  private Caller caller(Long accountId) {
    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    Account account =
        courseRepository
            .findAccount(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
    String ownerWallet = chainConfigRepository.findActive().map(ChainConfig::getOwnerWalletPubkey).orElse(null);
    boolean owner = CourseRules.isOwner(account.getRole(), account.getWalletPubkey(), ownerWallet);
    LocalDateTime now = LocalDateTime.now();
    return new Caller(
        account,
        owner,
        courseRepository.hasActivePlan(accountId, now),
        courseRepository.findEnigmaLevel(accountId).orElse(null),
        now);
  }

  private Course requireVisibleCourse(Caller caller, Long courseId) {
    Course course =
        courseRepository
            .findCourse(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    if (!"published".equals(course.getStatus()) && !privileged(caller, course)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
    }
    return course;
  }

  private boolean privileged(Caller caller, Course course) {
    return caller.owner() || caller.account().getId().equals(course.getAuthorAccountId());
  }

  private boolean watch(Caller caller, Course course, boolean freePreview) {
    boolean privileged = privileged(caller, course);
    boolean granted =
        !freePreview
            && !privileged
            && courseRepository.hasActiveGrant(caller.account().getWalletPubkey(), course.getId(), caller.now());
    return CourseRules.canWatch(freePreview, privileged, granted, caller.activePlan(), caller.level(), minLevel(course));
  }

  private Map<String, Object> access(Caller caller, Course course) {
    boolean canWatch = watch(caller, course, false);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("canWatch", canWatch);
    out.put("lockReason", canWatch ? null : CourseRules.lockReason(caller.activePlan(), caller.level(), minLevel(course)));
    out.put("activePlan", caller.activePlan());
    out.put("level", caller.level());
    out.put("minLevel", minLevel(course));
    return out;
  }

  private List<CourseSection> activeSections(Long courseId) {
    return courseRepository.findSections(courseId).stream().filter(s -> flag(s.getIsActive())).toList();
  }

  private List<Lesson> visibleLessons(Long courseId) {
    return visibleLessons(courseId, activeSections(courseId));
  }

  private List<Lesson> visibleLessons(Long courseId, List<CourseSection> activeSections) {
    Set<Long> sectionIds = activeSections.stream().map(CourseSection::getId).collect(Collectors.toSet());
    return courseRepository.findLessonsByCourse(courseId).stream()
        .filter(l -> flag(l.getIsActive()) && sectionIds.contains(l.getSectionId()))
        .toList();
  }

  private Set<Long> completedIds(Caller caller, List<Lesson> lessons) {
    Map<Long, Lesson> byId = lessons.stream().collect(Collectors.toMap(Lesson::getId, Function.identity()));
    return courseRepository.findProgress(caller.account().getId(), byId.keySet()).stream()
        .filter(p -> p.getCompletedAt() != null)
        .map(LessonProgress::getLessonId)
        .collect(Collectors.toSet());
  }

  private static Lesson withoutPaidContent(Lesson lesson) {
    Lesson copy = new Lesson(lesson);
    copy.setVideoUrl(null);
    copy.setEmbedUrl(null);
    copy.setResources(null);
    copy.setPracticePrompt(null);
    clearOfficialMedia(copy);
    return copy;
  }

  /** Official lessons never expose a permanent link or storage detail, even to viewers who may watch. */
  private static Lesson withoutOfficialMedia(Lesson lesson) {
    if (!MediaLinks.isOfficial(lesson)) {
      return lesson;
    }
    Lesson copy = new Lesson(lesson);
    copy.setVideoUrl(null);
    copy.setEmbedUrl(null);
    clearOfficialMedia(copy);
    return copy;
  }

  private static void clearOfficialMedia(Lesson lesson) {
    lesson.setContentHash(null);
    lesson.setStorageRef(null);
    lesson.setKeyId(null);
  }

  private static int indexOf(List<Lesson> lessons, Long lessonId) {
    for (int i = 0; i < lessons.size(); i++) {
      if (lessons.get(i).getId().equals(lessonId)) {
        return i;
      }
    }
    return -1;
  }

  private static int position(Integer raw) {
    if (raw == null) {
      return 0;
    }
    if (raw < 0 || raw > MAX_POSITION_SEC) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "positionSec must be between 0 and 86400");
    }
    return raw;
  }

  private static int minLevel(Course course) {
    return course.getMinLevel() == null ? 0 : course.getMinLevel();
  }

  private static boolean flag(Byte value) {
    return value != null && value != 0;
  }

  private static long nz(Integer value) {
    return value == null ? 0 : value;
  }

  private static ResponseStatusException lessonNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
  }

  private record Caller(Account account, boolean owner, boolean activePlan, Integer level, LocalDateTime now) {}
}
