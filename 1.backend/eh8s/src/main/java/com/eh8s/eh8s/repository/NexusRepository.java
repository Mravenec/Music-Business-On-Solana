package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.EnigmaEvaluationRecord;
import com.eh8s.eh8s.repository.interfaces.INexusRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.INSTRUMENT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_LEVEL_CHANGE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ENIGMA_EVALUATION;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.INSTRUCTOR_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.LESSON;
import static com.eh8s.eh8s.database.jooq.eh8s_role.Tables.ACCOUNT_ROLE;

/**
 * JOOQ persistence for NEXUS Score Enigma evaluations.
 */
@Repository
public class NexusRepository implements INexusRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public NexusRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Account> findAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOptionalInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<MusicianProfile> findProfile(Long musicianProfileId) {
    return dsl.selectFrom(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ID.eq(musicianProfileId))
        .fetchOptionalInto(MusicianProfile.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Long> findProfileIdByAccount(Long accountId) {
    return dsl.select(MUSICIAN_PROFILE.ID)
        .from(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .fetchOptional(MUSICIAN_PROFILE.ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInstructor(Long accountId) {
    return dsl.fetchExists(INSTRUCTOR_PROFILE, INSTRUCTOR_PROFILE.ACCOUNT_ID.eq(accountId))
        || dsl.fetchExists(
            ACCOUNT_ROLE, ACCOUNT_ROLE.ACCOUNT_ID.eq(accountId).and(ACCOUNT_ROLE.ROLE.eq("instructor")));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<String> findInstrumentCode(Long instrumentId) {
    return dsl.select(INSTRUMENT.CODE)
        .from(INSTRUMENT)
        .where(INSTRUMENT.ID.eq(instrumentId))
        .fetchOptional(INSTRUMENT.CODE);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Lesson> findLesson(Long lessonId) {
    return dsl.selectFrom(LESSON).where(LESSON.ID.eq(lessonId)).fetchOptionalInto(Lesson.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EnigmaLevel> findLevels() {
    return dsl.selectFrom(ENIGMA_LEVEL)
        .orderBy(ENIGMA_LEVEL.LEVEL_NUMBER)
        .fetchInto(EnigmaLevel.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EnigmaEvaluation insertEvaluation(EnigmaEvaluation evaluation) {
    EnigmaEvaluationRecord rec = dsl.newRecord(ENIGMA_EVALUATION, evaluation);
    rec.changed(ENIGMA_EVALUATION.ID, false);
    rec.store();
    return rec.into(EnigmaEvaluation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateEnigmaScore(Long musicianProfileId, byte score) {
    dsl.update(MUSICIAN_PROFILE)
        .set(MUSICIAN_PROFILE.ENIGMA_SCORE, score)
        .where(MUSICIAN_PROFILE.ID.eq(musicianProfileId))
        .execute();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EnigmaEvaluation> findEvaluations(Long musicianProfileId) {
    return dsl.selectFrom(ENIGMA_EVALUATION)
        .where(ENIGMA_EVALUATION.MUSICIAN_PROFILE_ID.eq(musicianProfileId))
        .orderBy(ENIGMA_EVALUATION.ID.desc())
        .fetchInto(EnigmaEvaluation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<EnigmaEvaluation> findEvaluation(Long evaluationId) {
    return dsl.selectFrom(ENIGMA_EVALUATION)
        .where(ENIGMA_EVALUATION.ID.eq(evaluationId))
        .fetchOptionalInto(EnigmaEvaluation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<MusicianLevelChange> findLevelChangeSince(
      Long musicianProfileId, byte newLevel, LocalDateTime since) {
    return dsl.selectFrom(MUSICIAN_LEVEL_CHANGE)
        .where(
            MUSICIAN_LEVEL_CHANGE
                .MUSICIAN_PROFILE_ID
                .eq(musicianProfileId)
                .and(MUSICIAN_LEVEL_CHANGE.NEW_LEVEL.eq(newLevel))
                .and(MUSICIAN_LEVEL_CHANGE.CREATED_AT.ge(since)))
        .orderBy(MUSICIAN_LEVEL_CHANGE.ID)
        .limit(1)
        .fetchOptionalInto(MusicianLevelChange.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void markApplied(Long evaluationId, LocalDateTime appliedAt) {
    dsl.update(ENIGMA_EVALUATION)
        .set(ENIGMA_EVALUATION.APPLIED_AT, appliedAt)
        .where(ENIGMA_EVALUATION.ID.eq(evaluationId))
        .execute();
  }
}
