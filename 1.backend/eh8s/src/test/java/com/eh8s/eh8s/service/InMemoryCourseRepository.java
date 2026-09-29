package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.LessonProgress;
import com.eh8s.eh8s.repository.interfaces.ICourseRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory {@link ICourseRepository} for service tests. */
class InMemoryCourseRepository implements ICourseRepository {

  final Map<Long, Account> accounts = new HashMap<>();
  final Map<Long, Integer> levels = new HashMap<>();
  final Set<Long> activePlans = new HashSet<>();
  final Set<Long> instructors = new HashSet<>();
  final Map<Long, Course> courses = new HashMap<>();
  final Map<Long, CourseSection> sections = new HashMap<>();
  final Map<Long, Lesson> lessons = new HashMap<>();
  final List<LessonProgress> progress = new ArrayList<>();
  final Map<Long, CourseAccessGrant> grants = new HashMap<>();
  private long nextId = 100;

  Account account(long id, String role, String wallet) {
    Account a = new Account();
    a.setId(id);
    a.setRole(role);
    a.setWalletPubkey(wallet);
    accounts.put(id, a);
    return a;
  }

  @Override
  public Optional<Account> findAccount(Long accountId) {
    return Optional.ofNullable(accounts.get(accountId));
  }

  @Override
  public Optional<Integer> findEnigmaLevel(Long accountId) {
    return Optional.ofNullable(levels.get(accountId));
  }

  @Override
  public boolean hasActivePlan(Long accountId, LocalDateTime now) {
    return activePlans.contains(accountId);
  }

  @Override
  public boolean hasActiveGrant(String walletPubkey, Long courseId, LocalDateTime now) {
    return grants.values().stream()
        .anyMatch(
            g ->
                g.getWalletPubkey().equals(walletPubkey)
                    && g.getIsActive() == 1
                    && (g.getCourseId() == null || g.getCourseId().equals(courseId))
                    && (g.getExpiresAt() == null || g.getExpiresAt().isAfter(now)));
  }

  @Override
  public boolean isInstructor(Long accountId) {
    return instructors.contains(accountId);
  }

  @Override
  public boolean instrumentExists(Long instrumentId) {
    return instrumentId == 1L;
  }

  @Override
  public List<Course> findCourses(Collection<String> statuses) {
    return courses.values().stream()
        .filter(c -> statuses.contains(c.getStatus()))
        .sorted(Comparator.comparing(Course::getSortOrder).thenComparing(Course::getId))
        .map(Course::new)
        .toList();
  }

  @Override
  public Optional<Course> findCourse(Long courseId) {
    return Optional.ofNullable(courses.get(courseId)).map(Course::new);
  }

  @Override
  public Course insertCourse(Course course) {
    Course c = new Course(course);
    c.setId(nextId++);
    courses.put(c.getId(), c);
    return new Course(c);
  }

  @Override
  public void updateCourse(Course course) {
    courses.put(course.getId(), new Course(course));
  }

  @Override
  public List<CourseSection> findSections(Long courseId) {
    return sections.values().stream()
        .filter(s -> s.getCourseId().equals(courseId))
        .sorted(Comparator.comparing(CourseSection::getSortOrder).thenComparing(CourseSection::getId))
        .map(CourseSection::new)
        .toList();
  }

  @Override
  public Optional<CourseSection> findSection(Long sectionId) {
    return Optional.ofNullable(sections.get(sectionId)).map(CourseSection::new);
  }

  @Override
  public CourseSection insertSection(CourseSection section) {
    CourseSection s = new CourseSection(section);
    s.setId(nextId++);
    sections.put(s.getId(), s);
    return new CourseSection(s);
  }

  @Override
  public void updateSection(CourseSection section) {
    sections.put(section.getId(), new CourseSection(section));
  }

  @Override
  public void reorderSections(List<Long> orderedIds) {
    for (int i = 0; i < orderedIds.size(); i++) {
      sections.get(orderedIds.get(i)).setSortOrder(i + 1);
    }
  }

  @Override
  public List<Lesson> findLessonsByCourse(Long courseId) {
    List<Lesson> out = new ArrayList<>();
    for (CourseSection section : findSections(courseId)) {
      out.addAll(findLessonsBySection(section.getId()));
    }
    return out;
  }

  @Override
  public List<Lesson> findLessonsBySection(Long sectionId) {
    return lessons.values().stream()
        .filter(l -> l.getSectionId().equals(sectionId))
        .sorted(Comparator.comparing(Lesson::getSortOrder).thenComparing(Lesson::getId))
        .map(Lesson::new)
        .toList();
  }

  @Override
  public Optional<Lesson> findLesson(Long lessonId) {
    return Optional.ofNullable(lessons.get(lessonId)).map(Lesson::new);
  }

  @Override
  public Lesson insertLesson(Lesson lesson) {
    Lesson l = new Lesson(lesson);
    l.setId(nextId++);
    lessons.put(l.getId(), l);
    return new Lesson(l);
  }

  @Override
  public void updateLesson(Lesson lesson) {
    lessons.put(lesson.getId(), new Lesson(lesson));
  }

  @Override
  public void reorderLessons(List<Long> orderedIds) {
    for (int i = 0; i < orderedIds.size(); i++) {
      lessons.get(orderedIds.get(i)).setSortOrder(i + 1);
    }
  }

  @Override
  public List<LessonProgress> findProgress(Long accountId, Collection<Long> lessonIds) {
    return progress.stream()
        .filter(p -> p.getAccountId().equals(accountId) && lessonIds.contains(p.getLessonId()))
        .map(LessonProgress::new)
        .toList();
  }

  @Override
  public LessonProgress upsertProgress(LessonProgress row) {
    progress.removeIf(p -> p.getAccountId().equals(row.getAccountId()) && p.getLessonId().equals(row.getLessonId()));
    LessonProgress stored = new LessonProgress(row);
    stored.setId(nextId++);
    progress.add(stored);
    return new LessonProgress(stored);
  }

  @Override
  public List<CourseAccessGrant> findGrants() {
    return grants.values().stream().map(CourseAccessGrant::new).toList();
  }

  @Override
  public Optional<CourseAccessGrant> findGrant(Long grantId) {
    return Optional.ofNullable(grants.get(grantId)).map(CourseAccessGrant::new);
  }

  @Override
  public Optional<CourseAccessGrant> findGrantByScope(String walletPubkey, Long courseScope) {
    return grants.values().stream()
        .filter(
            g ->
                g.getWalletPubkey().equals(walletPubkey)
                    && (g.getCourseId() == null ? 0L : g.getCourseId()) == courseScope)
        .findFirst()
        .map(CourseAccessGrant::new);
  }

  @Override
  public CourseAccessGrant insertGrant(CourseAccessGrant grant) {
    CourseAccessGrant g = new CourseAccessGrant(grant);
    g.setId(nextId++);
    grants.put(g.getId(), g);
    return new CourseAccessGrant(g);
  }

  @Override
  public void updateGrant(CourseAccessGrant grant) {
    grants.put(grant.getId(), new CourseAccessGrant(grant));
  }
}
