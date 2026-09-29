package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for NEXUS Score Enigma evaluations.
 */
public interface INexusRepository {

  /**
   * Loads an account.
   *
   * @param accountId account id
   * @return account when present
   */
  Optional<Account> findAccount(Long accountId);

  /**
   * Loads a musician profile.
   *
   * @param musicianProfileId profile id
   * @return profile when present
   */
  Optional<MusicianProfile> findProfile(Long musicianProfileId);

  /**
   * Resolves the musician profile owned by an account.
   *
   * @param accountId account id
   * @return profile id when the account is a musician
   */
  Optional<Long> findProfileIdByAccount(Long accountId);

  /**
   * Whether the account teaches: an instructor_profile row or a granted instructor role.
   *
   * @param accountId account id
   * @return true for teachers
   */
  boolean isInstructor(Long accountId);

  /**
   * Instrument code for a profile's instrument.
   *
   * @param instrumentId instrument id
   * @return instrument code when present
   */
  Optional<String> findInstrumentCode(Long instrumentId);

  /**
   * Loads an academy lesson so a practice recording can be linked to it.
   *
   * @param lessonId lesson id
   * @return lesson when present
   */
  Optional<Lesson> findLesson(Long lessonId);

  /**
   * Enigma level ladder ordered by level number.
   *
   * @return levels 0..5
   */
  List<EnigmaLevel> findLevels();

  /**
   * Inserts an evaluation.
   *
   * @param evaluation unsaved evaluation
   * @return stored evaluation with id
   */
  EnigmaEvaluation insertEvaluation(EnigmaEvaluation evaluation);

  /**
   * Stores the latest Score Enigma on the musician profile.
   *
   * @param musicianProfileId profile id
   * @param score score 0..100
   */
  void updateEnigmaScore(Long musicianProfileId, byte score);

  /**
   * Evaluations for one musician, newest first.
   *
   * @param musicianProfileId profile id
   * @return evaluations
   */
  List<EnigmaEvaluation> findEvaluations(Long musicianProfileId);

  /**
   * Loads one evaluation.
   *
   * @param evaluationId evaluation id
   * @return evaluation when present
   */
  Optional<EnigmaEvaluation> findEvaluation(Long evaluationId);

  /**
   * First verified level change to a level at or after a moment.
   *
   * @param musicianProfileId profile id
   * @param newLevel level the change must land on
   * @param since earliest change time
   * @return matching change when present
   */
  Optional<MusicianLevelChange> findLevelChangeSince(
      Long musicianProfileId, byte newLevel, LocalDateTime since);

  /**
   * Marks an evaluation's level recommendation as applied on-chain.
   *
   * @param evaluationId evaluation id
   * @param appliedAt time of the verified level change
   */
  void markApplied(Long evaluationId, LocalDateTime appliedAt);
}
