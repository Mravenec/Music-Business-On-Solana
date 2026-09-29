package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.ICourseRepository;
import com.eh8s.eh8s.service.course.CourseRules;
import com.eh8s.eh8s.service.course.VideoLinks;
import com.eh8s.eh8s.service.interfaces.ICourseAuthoringService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authoring side of the academy video library. The owner authors every course; instructors with
 * Enigma level 5 author their own drafts and submit them for owner review.
 */
@Service
public class CourseAuthoringService implements ICourseAuthoringService {

  private static final Pattern WALLET = Pattern.compile("^[1-9A-HJ-NP-Za-km-z]{32,44}$");
  private static final String ACCESS_OWNER_ONLY = "Only the platform owner can manage course access";
  private static final String REVIEW_OWNER_ONLY = "Only the platform owner can review courses";

  private final ICourseRepository courseRepository;
  private final IChainConfigRepository chainConfigRepository;

  /**
   * Creates the service.
   *
   * @param courseRepository course persistence
   * @param chainConfigRepository chain config (owner wallet)
   */
  public CourseAuthoringService(
      ICourseRepository courseRepository, IChainConfigRepository chainConfigRepository) {
    this.courseRepository = courseRepository;
    this.chainConfigRepository = chainConfigRepository;
  }

  /** {@inheritDoc} */
  @Override
  public List<Map<String, Object>> courses(Long accountId) {
    Author author = requireAuthor(accountId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Course course : courseRepository.findCourses(CourseRules.STATUSES)) {
      if (!canAuthor(author, course)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("course", course);
      row.put("authorName", authorName(course));
      row.put("sectionCount", courseRepository.findSections(course.getId()).size());
      row.put("lessonCount", courseRepository.findLessonsByCourse(course.getId()).size());
      out.add(row);
    }
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> course(Long accountId, Long courseId) {
    Course course = requireCourse(requireAuthor(accountId), courseId);
    List<Lesson> lessons = courseRepository.findLessonsByCourse(course.getId());
    List<Map<String, Object>> sections = new ArrayList<>();
    for (CourseSection section : courseRepository.findSections(course.getId())) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("section", section);
      row.put("lessons", lessons.stream().filter(l -> l.getSectionId().equals(section.getId())).toList());
      sections.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("course", course);
    out.put("sections", sections);
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Course createCourse(Long accountId, Course input) {
    Author author = requireAuthor(accountId);
    Course in = input == null ? new Course() : input;
    Course course = new Course();
    applyCourseFields(course, in);
    course.setStatus("draft");
    course.setReviewStatus("draft");
    course.setAuthorAccountId(author.account().getId());
    course.setSortOrder(courseRepository.findCourses(CourseRules.STATUSES).size() + 1);
    return courseRepository.insertCourse(course);
  }

  /** {@inheritDoc} */
  @Override
  public Course updateCourse(Long accountId, Long courseId, Course input) {
    Course course = requireEditable(requireAuthor(accountId), courseId);
    applyCourseFields(course, input == null ? new Course() : input);
    courseRepository.updateCourse(course);
    return courseRepository.findCourse(courseId).orElse(course);
  }

  /** {@inheritDoc} */
  @Override
  public List<Course> moveCourse(Long accountId, Long courseId, Integer sortOrder) {
    Account owner = requireOwner(accountId, "Only the platform owner can reorder the catalog");
    requireCourse(new Author(owner, true), courseId);
    List<Course> all = courseRepository.findCourses(CourseRules.STATUSES);
    List<Long> order =
        CourseRules.moveTo(all.stream().map(Course::getId).toList(), courseId, sortOrder(sortOrder));
    for (Course course : all) {
      int position = order.indexOf(course.getId()) + 1;
      if (course.getSortOrder() == null || course.getSortOrder() != position) {
        course.setSortOrder(position);
        courseRepository.updateCourse(course);
      }
    }
    return courseRepository.findCourses(CourseRules.STATUSES);
  }

  /** {@inheritDoc} */
  @Override
  public Course setStatus(Long accountId, Long courseId, String status) {
    Account owner = requireOwner(accountId, "Only the platform owner can publish, unpublish or archive courses");
    Course course = requireCourse(new Author(owner, true), courseId);
    if (!CourseRules.STATUSES.contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be draft, published or archived");
    }
    if ("published".equals(status)) {
      return publish(owner, course);
    }
    if ("draft".equals(status)) {
      course.setReviewStatus("draft");
    }
    course.setStatus(status);
    courseRepository.updateCourse(course);
    return courseRepository.findCourse(courseId).orElse(course);
  }

  /** {@inheritDoc} */
  @Override
  public CourseSection createSection(Long accountId, Long courseId, CourseSection input) {
    Course course = requireEditable(requireAuthor(accountId), courseId);
    CourseSection section = new CourseSection();
    section.setCourseId(course.getId());
    section.setTitle(text(input == null ? null : input.getTitle(), "title", 160, true));
    section.setSortOrder(courseRepository.findSections(course.getId()).size() + 1);
    section.setIsActive((byte) 1);
    return courseRepository.insertSection(section);
  }

  /** {@inheritDoc} */
  @Override
  public CourseSection updateSection(Long accountId, Long sectionId, CourseSection input) {
    CourseSection section = requireSection(requireAuthor(accountId), sectionId);
    CourseSection in = input == null ? new CourseSection() : input;
    section.setTitle(text(in.getTitle(), "title", 160, true));
    if (in.getIsActive() != null) {
      section.setIsActive(flagInput(in.getIsActive(), "isActive"));
    }
    courseRepository.updateSection(section);
    return courseRepository.findSection(sectionId).orElse(section);
  }

  /** {@inheritDoc} */
  @Override
  public List<CourseSection> moveSection(Long accountId, Long sectionId, Integer sortOrder) {
    CourseSection section = requireSection(requireAuthor(accountId), sectionId);
    List<Long> ids =
        courseRepository.findSections(section.getCourseId()).stream().map(CourseSection::getId).toList();
    courseRepository.reorderSections(CourseRules.moveTo(ids, sectionId, sortOrder(sortOrder)));
    return courseRepository.findSections(section.getCourseId());
  }

  /** {@inheritDoc} */
  @Override
  public Lesson createLesson(Long accountId, Long sectionId, Lesson input) {
    CourseSection section = requireSection(requireAuthor(accountId), sectionId);
    Lesson in = input == null ? new Lesson() : input;
    Lesson lesson = new Lesson();
    lesson.setSectionId(section.getId());
    applyLessonFields(lesson, in);
    lesson.setIsFreePreview(in.getIsFreePreview() == null ? (byte) 0 : flagInput(in.getIsFreePreview(), "isFreePreview"));
    lesson.setIsActive((byte) 1);
    lesson.setSortOrder(courseRepository.findLessonsBySection(section.getId()).size() + 1);
    return courseRepository.insertLesson(lesson);
  }

  /** {@inheritDoc} */
  @Override
  public Lesson updateLesson(Long accountId, Long lessonId, Lesson input) {
    Lesson lesson = requireLesson(requireAuthor(accountId), lessonId);
    Lesson in = input == null ? new Lesson() : input;
    applyLessonFields(lesson, in);
    if (in.getIsFreePreview() != null) {
      lesson.setIsFreePreview(flagInput(in.getIsFreePreview(), "isFreePreview"));
    }
    if (in.getIsActive() != null) {
      lesson.setIsActive(flagInput(in.getIsActive(), "isActive"));
    }
    courseRepository.updateLesson(lesson);
    return courseRepository.findLesson(lessonId).orElse(lesson);
  }

  /** {@inheritDoc} */
  @Override
  public List<Lesson> moveLesson(Long accountId, Long lessonId, Integer sortOrder) {
    Lesson lesson = requireLesson(requireAuthor(accountId), lessonId);
    List<Long> ids =
        courseRepository.findLessonsBySection(lesson.getSectionId()).stream().map(Lesson::getId).toList();
    courseRepository.reorderLessons(CourseRules.moveTo(ids, lessonId, sortOrder(sortOrder)));
    return courseRepository.findLessonsBySection(lesson.getSectionId());
  }

  /** {@inheritDoc} */
  @Override
  public List<CourseAccessGrant> grants(Long accountId) {
    requireOwner(accountId, ACCESS_OWNER_ONLY);
    return courseRepository.findGrants();
  }

  /** {@inheritDoc} */
  @Override
  public CourseAccessGrant grant(Long accountId, CourseAccessGrant input) {
    Account owner = requireOwner(accountId, ACCESS_OWNER_ONLY);
    CourseAccessGrant in = input == null ? new CourseAccessGrant() : input;
    String wallet = text(in.getWalletPubkey(), "walletPubkey", 44, true);
    if (!WALLET.matcher(wallet).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey must be a Solana address");
    }
    Long courseId = in.getCourseId();
    if (courseId != null && courseRepository.findCourse(courseId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown courseId");
    }
    LocalDateTime expiresAt = expiresAt(in.getExpiresAt());
    String note = text(in.getNote(), "note", 200, false);

    CourseAccessGrant existing =
        courseRepository.findGrantByScope(wallet, courseId == null ? 0L : courseId).orElse(null);
    if (existing != null) {
      existing.setExpiresAt(expiresAt);
      existing.setNote(note);
      existing.setIsActive((byte) 1);
      existing.setGrantedByAccountId(owner.getId());
      courseRepository.updateGrant(existing);
      return courseRepository.findGrant(existing.getId()).orElse(existing);
    }
    CourseAccessGrant grant = new CourseAccessGrant();
    grant.setCourseId(courseId);
    grant.setWalletPubkey(wallet);
    grant.setGrantedByAccountId(owner.getId());
    grant.setNote(note);
    grant.setExpiresAt(expiresAt);
    grant.setIsActive((byte) 1);
    return courseRepository.insertGrant(grant);
  }

  /** {@inheritDoc} */
  @Override
  public CourseAccessGrant revokeGrant(Long accountId, Long grantId) {
    Account owner = requireOwner(accountId, ACCESS_OWNER_ONLY);
    CourseAccessGrant grant =
        courseRepository
            .findGrant(grantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grant not found"));
    grant.setIsActive((byte) 0);
    grant.setGrantedByAccountId(owner.getId());
    courseRepository.updateGrant(grant);
    return courseRepository.findGrant(grantId).orElse(grant);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> eligibility(Long accountId) {
    Account account = requireAccount(accountId);
    boolean owner = isOwner(account);
    Integer level = courseRepository.findEnigmaLevel(account.getId()).orElse(null);
    boolean instructor = courseRepository.isInstructor(account.getId());
    boolean levelMet = level != null && level >= CourseRules.TEACH_LEVEL;

    List<Map<String, Object>> requirements = new ArrayList<>();
    requirements.add(
        requirement(
            "level",
            "Reach Enigma level " + CourseRules.TEACH_LEVEL,
            levelMet,
            level == null ? "No musician profile yet" : "Your level: " + level));
    requirements.add(
        requirement(
            "instructor",
            "Approved instructor role",
            instructor,
            instructor ? "Approved" : "Apply for the instructor role and wait for the owner"));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("canTeach", owner || CourseRules.canTeach(instructor, level));
    out.put("owner", owner);
    out.put("level", level);
    out.put("requiredLevel", CourseRules.TEACH_LEVEL);
    out.put("instructor", instructor);
    out.put("requirements", requirements);
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Course submit(Long accountId, Long courseId) {
    Author author = requireAuthor(accountId);
    Course course = requireCourse(author, courseId);
    if (!author.account().getId().equals(course.getAuthorAccountId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the course author can submit it for review");
    }
    if (!CourseRules.authorCanEdit(course.getStatus(), course.getReviewStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft course can be submitted for review");
    }
    if (!hasVisibleLesson(courseId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Add at least one active lesson in an active section before submitting");
    }
    course.setReviewStatus("submitted");
    course.setSubmittedAt(LocalDateTime.now());
    course.setReviewNote(null);
    course.setReviewedAt(null);
    course.setReviewedByAccountId(null);
    courseRepository.updateCourse(course);
    return courseRepository.findCourse(courseId).orElse(course);
  }

  /** {@inheritDoc} */
  @Override
  public List<Map<String, Object>> reviewQueue(Long accountId) {
    requireOwner(accountId, REVIEW_OWNER_ONLY);
    List<Map<String, Object>> out = new ArrayList<>();
    courseRepository.findCourses(CourseRules.STATUSES).stream()
        .filter(c -> "submitted".equals(c.getReviewStatus()))
        .sorted(
            Comparator.comparing(
                Course::getSubmittedAt, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
        .forEach(
            course -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("course", course);
              row.put("authorName", authorName(course));
              row.put("sectionCount", courseRepository.findSections(course.getId()).size());
              row.put("lessonCount", courseRepository.findLessonsByCourse(course.getId()).size());
              out.add(row);
            });
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Course approve(Long accountId, Long courseId) {
    Account owner = requireOwner(accountId, REVIEW_OWNER_ONLY);
    Course course = requireSubmitted(courseId);
    return publish(owner, course);
  }

  /** {@inheritDoc} */
  @Override
  public Course reject(Long accountId, Long courseId, String note) {
    Account owner = requireOwner(accountId, REVIEW_OWNER_ONLY);
    Course course = requireSubmitted(courseId);
    course.setReviewStatus("rejected");
    course.setReviewNote(text(note, "note", 500, true));
    course.setReviewedByAccountId(owner.getId());
    course.setReviewedAt(LocalDateTime.now());
    courseRepository.updateCourse(course);
    return courseRepository.findCourse(courseId).orElse(course);
  }

  /**
   * Single place that decides which courses an author may touch: the owner every course, an
   * eligible instructor only the courses they created.
   *
   * @param author resolved author
   * @param course course, or {@code null} when creating a new one
   * @return {@code true} when the author may work on the course
   */
  boolean canAuthor(Author author, Course course) {
    return author.owner() || course == null || author.account().getId().equals(course.getAuthorAccountId());
  }

  /** Caller that passed the authoring gate. */
  record Author(Account account, boolean owner) {}

  private Author requireAuthor(Long accountId) {
    Account account = requireAccount(accountId);
    if (isOwner(account)) {
      return new Author(account, true);
    }
    boolean instructor = courseRepository.isInstructor(account.getId());
    Integer level = courseRepository.findEnigmaLevel(account.getId()).orElse(null);
    if (!CourseRules.canTeach(instructor, level)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Teaching needs Enigma level " + CourseRules.TEACH_LEVEL + " and an approved instructor role");
    }
    return new Author(account, false);
  }

  private Course requireSubmitted(Long courseId) {
    Course course =
        courseRepository
            .findCourse(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    if (!"submitted".equals(course.getReviewStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a submitted course can be reviewed");
    }
    return course;
  }

  private Course publish(Account owner, Course course) {
    if (!hasVisibleLesson(course.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Add at least one active lesson in an active section before publishing");
    }
    LocalDateTime now = LocalDateTime.now();
    if (course.getPublishedAt() == null) {
      course.setPublishedAt(now);
    }
    course.setStatus("published");
    course.setReviewStatus("approved");
    course.setReviewedByAccountId(owner.getId());
    course.setReviewedAt(now);
    courseRepository.updateCourse(course);
    return courseRepository.findCourse(course.getId()).orElse(course);
  }

  private boolean hasVisibleLesson(Long courseId) {
    Set<Long> activeSections =
        new HashSet<>(
            courseRepository.findSections(courseId).stream()
                .filter(s -> flag(s.getIsActive()))
                .map(CourseSection::getId)
                .toList());
    return courseRepository.findLessonsByCourse(courseId).stream()
        .anyMatch(l -> flag(l.getIsActive()) && activeSections.contains(l.getSectionId()));
  }

  private String authorName(Course course) {
    return courseRepository.findAccount(course.getAuthorAccountId()).map(Account::getDisplayName).orElse(null);
  }

  private static Map<String, Object> requirement(String key, String label, boolean met, String detail) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("key", key);
    out.put("label", label);
    out.put("met", met);
    out.put("detail", detail);
    return out;
  }

  private Account requireOwner(Long accountId, String message) {
    Account account = requireAccount(accountId);
    if (!isOwner(account)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
    return account;
  }

  private Account requireAccount(Long accountId) {
    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    return courseRepository
        .findAccount(accountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
  }

  private boolean isOwner(Account account) {
    String ownerWallet = chainConfigRepository.findActive().map(ChainConfig::getOwnerWalletPubkey).orElse(null);
    return CourseRules.isOwner(account.getRole(), account.getWalletPubkey(), ownerWallet);
  }

  private Course requireCourse(Author author, Long courseId) {
    Course course =
        courseRepository
            .findCourse(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    if (!canAuthor(author, course)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own courses");
    }
    return course;
  }

  private Course requireEditable(Author author, Long courseId) {
    Course course = requireCourse(author, courseId);
    if (!author.owner() && !CourseRules.authorCanEdit(course.getStatus(), course.getReviewStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This course is in review or published. The owner can move it back to draft.");
    }
    return course;
  }

  private CourseSection requireSection(Author author, Long sectionId) {
    CourseSection section =
        courseRepository
            .findSection(sectionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
    requireEditable(author, section.getCourseId());
    return section;
  }

  private Lesson requireLesson(Author author, Long lessonId) {
    Lesson lesson =
        courseRepository
            .findLesson(lessonId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
    requireSection(author, lesson.getSectionId());
    return lesson;
  }

  private void applyCourseFields(Course course, Course in) {
    course.setTitle(text(in.getTitle(), "title", 160, true));
    course.setSummary(text(in.getSummary(), "summary", 1000, false));
    course.setInstrumentId(instrument(in.getInstrumentId()));
    course.setMinLevel((byte) level(in.getMinLevel()));
  }

  private static void applyLessonFields(Lesson lesson, Lesson in) {
    lesson.setTitle(text(in.getTitle(), "title", 160, true));
    lesson.setDescription(text(in.getDescription(), "description", 2000, false));
    VideoLinks.apply(lesson, text(in.getVideoUrl(), "videoUrl", VideoLinks.MAX_URL_LENGTH, true));
    lesson.setDurationSec(duration(in.getDurationSec()));
    lesson.setResources(text(in.getResources(), "resources", 2000, false));
    lesson.setPracticePrompt(text(in.getPracticePrompt(), "practicePrompt", 1000, false));
  }

  private Long instrument(Long id) {
    if (id == null) {
      return null;
    }
    if (!courseRepository.instrumentExists(id)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown instrumentId");
    }
    return id;
  }

  private static String text(String raw, String key, int max, boolean required) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      if (required) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
      }
      return null;
    }
    if (value.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be at most " + max + " characters");
    }
    return value;
  }

  private static int level(Byte raw) {
    if (raw == null) {
      return 0;
    }
    if (raw < 0 || raw > CourseRules.MAX_LEVEL) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minLevel must be between 0 and 5");
    }
    return raw;
  }

  private static Integer duration(Integer raw) {
    if (raw == null) {
      return null;
    }
    if (raw < 0 || raw > 24 * 3600) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationSec must be between 0 and 86400");
    }
    return raw;
  }

  private static int sortOrder(Integer raw) {
    if (raw == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortOrder is required");
    }
    if (raw < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortOrder must be 1 or more");
    }
    return raw;
  }

  private static byte flagInput(Byte raw, String field) {
    if (raw != 0 && raw != 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be 0 or 1");
    }
    return raw;
  }

  private static LocalDateTime expiresAt(LocalDateTime at) {
    if (at == null) {
      return null;
    }
    if (!at.isAfter(LocalDateTime.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
    }
    return at;
  }

  private static boolean flag(Byte value) {
    return value != null && value != 0;
  }
}
