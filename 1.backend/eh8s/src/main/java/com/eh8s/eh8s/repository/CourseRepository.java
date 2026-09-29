package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.INSTRUMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.COURSE;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.COURSE_ACCESS_GRANT;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.COURSE_SECTION;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.INSTRUCTOR_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.LESSON;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.LESSON_PROGRESS;
import static com.eh8s.eh8s.database.jooq.eh8s_role.Tables.ACCOUNT_ROLE;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Course;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseAccessGrant;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.CourseSection;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.LessonProgress;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.CourseAccessGrantRecord;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.CourseRecord;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.CourseSectionRecord;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.LessonRecord;
import com.eh8s.eh8s.repository.interfaces.ICourseRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * JOOQ persistence for the academy video library.
 */
@Repository
public class CourseRepository implements ICourseRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public CourseRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Account> findAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOptionalInto(Account.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Integer> findEnigmaLevel(Long accountId) {
    return dsl.select(ENIGMA_LEVEL.LEVEL_NUMBER)
        .from(MUSICIAN_PROFILE)
        .join(ENIGMA_LEVEL)
        .on(ENIGMA_LEVEL.ID.eq(MUSICIAN_PROFILE.ENIGMA_LEVEL_ID))
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .orderBy(ENIGMA_LEVEL.LEVEL_NUMBER.desc())
        .limit(1)
        .fetchOptional(ENIGMA_LEVEL.LEVEL_NUMBER)
        .map(Byte::intValue);
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasActivePlan(Long accountId, LocalDateTime now) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(ACADEMY_SUBSCRIPTION)
            .join(MUSICIAN_PROFILE)
            .on(MUSICIAN_PROFILE.ID.eq(ACADEMY_SUBSCRIPTION.MUSICIAN_PROFILE_ID))
            .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
            .and(
                ACADEMY_SUBSCRIPTION
                    .ON_CHAIN_STATUS
                    .eq("confirmed")
                    .or(ACADEMY_SUBSCRIPTION.PAID_AT.isNotNull()))
            .and(
                ACADEMY_SUBSCRIPTION
                    .ONCHAIN_EXPIRES_AT
                    .gt(LocalDateTime.now(ZoneOffset.UTC))
                    .or(
                        ACADEMY_SUBSCRIPTION
                            .ONCHAIN_EXPIRES_AT
                            .isNull()
                            .and(ACADEMY_SUBSCRIPTION.EXPIRES_AT.ge(now.toLocalDate())))));
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasActiveGrant(String walletPubkey, Long courseId, LocalDateTime now) {
    if (walletPubkey == null || walletPubkey.isBlank()) {
      return false;
    }
    return dsl.fetchExists(
        COURSE_ACCESS_GRANT,
        COURSE_ACCESS_GRANT
            .WALLET_PUBKEY
            .eq(walletPubkey)
            .and(COURSE_ACCESS_GRANT.IS_ACTIVE.eq((byte) 1))
            .and(COURSE_ACCESS_GRANT.COURSE_ID.isNull().or(COURSE_ACCESS_GRANT.COURSE_ID.eq(courseId)))
            .and(COURSE_ACCESS_GRANT.EXPIRES_AT.isNull().or(COURSE_ACCESS_GRANT.EXPIRES_AT.gt(now))));
  }

  /** {@inheritDoc} */
  @Override
  public boolean instrumentExists(Long instrumentId) {
    return dsl.fetchExists(INSTRUMENT, INSTRUMENT.ID.eq(instrumentId));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInstructor(Long accountId) {
    return dsl.fetchExists(ACCOUNT, ACCOUNT.ID.eq(accountId).and(ACCOUNT.ROLE.eq("instructor")))
        || dsl.fetchExists(INSTRUCTOR_PROFILE, INSTRUCTOR_PROFILE.ACCOUNT_ID.eq(accountId))
        || dsl.fetchExists(
            ACCOUNT_ROLE, ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId).and(ACCOUNT_ROLE.ROLE.eq("instructor")));
  }

  /** {@inheritDoc} */
  @Override
  public List<Course> findCourses(Collection<String> statuses) {
    return dsl.selectFrom(COURSE)
        .where(COURSE.STATUS.in(statuses))
        .orderBy(COURSE.SORT_ORDER, COURSE.ID)
        .fetchInto(Course.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Course> findCourse(Long courseId) {
    return dsl.selectFrom(COURSE).where(COURSE.ID.eq(courseId)).fetchOptionalInto(Course.class);
  }

  /** {@inheritDoc} */
  @Override
  public Course insertCourse(Course course) {
    CourseRecord rec = dsl.newRecord(COURSE, course);
    storeNew(rec);
    return rec.into(Course.class);
  }

  /** {@inheritDoc} */
  @Override
  public void updateCourse(Course course) {
    dsl.update(COURSE)
        .set(COURSE.TITLE, course.getTitle())
        .set(COURSE.SUMMARY, course.getSummary())
        .set(COURSE.INSTRUMENT_ID, course.getInstrumentId())
        .set(COURSE.MIN_LEVEL, course.getMinLevel())
        .set(COURSE.STATUS, course.getStatus())
        .set(COURSE.REVIEW_STATUS, course.getReviewStatus())
        .set(COURSE.REVIEW_NOTE, course.getReviewNote())
        .set(COURSE.REVIEWED_BY_ACCOUNT_ID, course.getReviewedByAccountId())
        .set(COURSE.SUBMITTED_AT, course.getSubmittedAt())
        .set(COURSE.REVIEWED_AT, course.getReviewedAt())
        .set(COURSE.SORT_ORDER, course.getSortOrder())
        .set(COURSE.PUBLISHED_AT, course.getPublishedAt())
        .set(COURSE.UPDATED_AT, LocalDateTime.now())
        .where(COURSE.ID.eq(course.getId()))
        .execute();
  }

  /** {@inheritDoc} */
  @Override
  public List<CourseSection> findSections(Long courseId) {
    return dsl.selectFrom(COURSE_SECTION)
        .where(COURSE_SECTION.COURSE_ID.eq(courseId))
        .orderBy(COURSE_SECTION.SORT_ORDER, COURSE_SECTION.ID)
        .fetchInto(CourseSection.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<CourseSection> findSection(Long sectionId) {
    return dsl.selectFrom(COURSE_SECTION)
        .where(COURSE_SECTION.ID.eq(sectionId))
        .fetchOptionalInto(CourseSection.class);
  }

  /** {@inheritDoc} */
  @Override
  public CourseSection insertSection(CourseSection section) {
    CourseSectionRecord rec = dsl.newRecord(COURSE_SECTION, section);
    storeNew(rec);
    return rec.into(CourseSection.class);
  }

  /** {@inheritDoc} */
  @Override
  public void updateSection(CourseSection section) {
    dsl.update(COURSE_SECTION)
        .set(COURSE_SECTION.TITLE, section.getTitle())
        .set(COURSE_SECTION.IS_ACTIVE, section.getIsActive())
        .set(COURSE_SECTION.SORT_ORDER, section.getSortOrder())
        .where(COURSE_SECTION.ID.eq(section.getId()))
        .execute();
  }

  /** {@inheritDoc} */
  @Override
  public void reorderSections(List<Long> orderedIds) {
    dsl.transaction(
        cfg -> {
          DSLContext tx = DSL.using(cfg);
          for (int i = 0; i < orderedIds.size(); i++) {
            tx.update(COURSE_SECTION)
                .set(COURSE_SECTION.SORT_ORDER, i + 1)
                .where(COURSE_SECTION.ID.eq(orderedIds.get(i)))
                .execute();
          }
        });
  }

  /** {@inheritDoc} */
  @Override
  public List<Lesson> findLessonsByCourse(Long courseId) {
    return dsl.select(LESSON.fields())
        .from(LESSON)
        .join(COURSE_SECTION)
        .on(COURSE_SECTION.ID.eq(LESSON.SECTION_ID))
        .where(COURSE_SECTION.COURSE_ID.eq(courseId))
        .orderBy(COURSE_SECTION.SORT_ORDER, COURSE_SECTION.ID, LESSON.SORT_ORDER, LESSON.ID)
        .fetchInto(Lesson.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Lesson> findLessonsBySection(Long sectionId) {
    return dsl.selectFrom(LESSON)
        .where(LESSON.SECTION_ID.eq(sectionId))
        .orderBy(LESSON.SORT_ORDER, LESSON.ID)
        .fetchInto(Lesson.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Lesson> findLesson(Long lessonId) {
    return dsl.selectFrom(LESSON).where(LESSON.ID.eq(lessonId)).fetchOptionalInto(Lesson.class);
  }

  /** {@inheritDoc} */
  @Override
  public Lesson insertLesson(Lesson lesson) {
    LessonRecord rec = dsl.newRecord(LESSON, lesson);
    storeNew(rec);
    return rec.into(Lesson.class);
  }

  /** {@inheritDoc} */
  @Override
  public void updateLesson(Lesson lesson) {
    dsl.update(LESSON)
        .set(LESSON.TITLE, lesson.getTitle())
        .set(LESSON.DESCRIPTION, lesson.getDescription())
        .set(LESSON.VIDEO_PROVIDER, lesson.getVideoProvider())
        .set(LESSON.VIDEO_URL, lesson.getVideoUrl())
        .set(LESSON.EMBED_URL, lesson.getEmbedUrl())
        .set(LESSON.DURATION_SEC, lesson.getDurationSec())
        .set(LESSON.SORT_ORDER, lesson.getSortOrder())
        .set(LESSON.IS_FREE_PREVIEW, lesson.getIsFreePreview())
        .set(LESSON.RESOURCES, lesson.getResources())
        .set(LESSON.PRACTICE_PROMPT, lesson.getPracticePrompt())
        .set(LESSON.IS_ACTIVE, lesson.getIsActive())
        .set(LESSON.UPDATED_AT, LocalDateTime.now())
        .where(LESSON.ID.eq(lesson.getId()))
        .execute();
  }

  /** {@inheritDoc} */
  @Override
  public void reorderLessons(List<Long> orderedIds) {
    dsl.transaction(
        cfg -> {
          DSLContext tx = DSL.using(cfg);
          for (int i = 0; i < orderedIds.size(); i++) {
            tx.update(LESSON)
                .set(LESSON.SORT_ORDER, i + 1)
                .where(LESSON.ID.eq(orderedIds.get(i)))
                .execute();
          }
        });
  }

  /** {@inheritDoc} */
  @Override
  public List<LessonProgress> findProgress(Long accountId, Collection<Long> lessonIds) {
    if (lessonIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(LESSON_PROGRESS)
        .where(LESSON_PROGRESS.ACCOUNT_ID.eq(accountId))
        .and(LESSON_PROGRESS.LESSON_ID.in(lessonIds))
        .fetchInto(LessonProgress.class);
  }

  /** {@inheritDoc} */
  @Override
  public LessonProgress upsertProgress(LessonProgress progress) {
    LocalDateTime now = LocalDateTime.now();
    dsl.insertInto(LESSON_PROGRESS)
        .set(LESSON_PROGRESS.ACCOUNT_ID, progress.getAccountId())
        .set(LESSON_PROGRESS.LESSON_ID, progress.getLessonId())
        .set(LESSON_PROGRESS.LAST_POSITION_SEC, progress.getLastPositionSec())
        .set(LESSON_PROGRESS.COMPLETED_AT, progress.getCompletedAt())
        .set(LESSON_PROGRESS.UPDATED_AT, now)
        .onDuplicateKeyUpdate()
        .set(LESSON_PROGRESS.LAST_POSITION_SEC, progress.getLastPositionSec())
        .set(LESSON_PROGRESS.COMPLETED_AT, progress.getCompletedAt())
        .set(LESSON_PROGRESS.UPDATED_AT, now)
        .execute();
    return dsl.selectFrom(LESSON_PROGRESS)
        .where(LESSON_PROGRESS.ACCOUNT_ID.eq(progress.getAccountId()))
        .and(LESSON_PROGRESS.LESSON_ID.eq(progress.getLessonId()))
        .fetchOneInto(LessonProgress.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<CourseAccessGrant> findGrants() {
    return dsl.selectFrom(COURSE_ACCESS_GRANT)
        .orderBy(COURSE_ACCESS_GRANT.CREATED_AT.desc(), COURSE_ACCESS_GRANT.ID.desc())
        .fetchInto(CourseAccessGrant.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<CourseAccessGrant> findGrant(Long grantId) {
    return dsl.selectFrom(COURSE_ACCESS_GRANT)
        .where(COURSE_ACCESS_GRANT.ID.eq(grantId))
        .fetchOptionalInto(CourseAccessGrant.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<CourseAccessGrant> findGrantByScope(String walletPubkey, Long courseScope) {
    return dsl.selectFrom(COURSE_ACCESS_GRANT)
        .where(COURSE_ACCESS_GRANT.WALLET_PUBKEY.eq(walletPubkey))
        .and(COURSE_ACCESS_GRANT.COURSE_SCOPE.eq(courseScope))
        .fetchOptionalInto(CourseAccessGrant.class);
  }

  /** {@inheritDoc} */
  @Override
  public CourseAccessGrant insertGrant(CourseAccessGrant grant) {
    CourseAccessGrantRecord rec = dsl.newRecord(COURSE_ACCESS_GRANT, grant);
    storeNew(rec);
    return rec.into(CourseAccessGrant.class);
  }

  /** {@inheritDoc} */
  @Override
  public void updateGrant(CourseAccessGrant grant) {
    dsl.update(COURSE_ACCESS_GRANT)
        .set(COURSE_ACCESS_GRANT.NOTE, grant.getNote())
        .set(COURSE_ACCESS_GRANT.EXPIRES_AT, grant.getExpiresAt())
        .set(COURSE_ACCESS_GRANT.IS_ACTIVE, grant.getIsActive())
        .set(COURSE_ACCESS_GRANT.GRANTED_BY_ACCOUNT_ID, grant.getGrantedByAccountId())
        .set(COURSE_ACCESS_GRANT.UPDATED_AT, LocalDateTime.now())
        .where(COURSE_ACCESS_GRANT.ID.eq(grant.getId()))
        .execute();
  }

  @SuppressWarnings("unchecked")
  private static void storeNew(UpdatableRecord<?> rec) {
    LocalDateTime now = LocalDateTime.now();
    for (String stamp : new String[] {"created_at", "updated_at"}) {
      Field<?> field = rec.field(stamp);
      if (field != null && field.getType() == LocalDateTime.class && rec.get(field) == null) {
        rec.set((Field<LocalDateTime>) field, now);
      }
    }
    for (Field<?> field : rec.fields()) {
      if (rec.get(field) == null) {
        rec.changed(field, false);
      }
    }
    rec.store();
    rec.refresh();
  }
}