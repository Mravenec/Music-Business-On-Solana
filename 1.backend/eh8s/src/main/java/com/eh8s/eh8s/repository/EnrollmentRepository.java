package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.AcademySubscriptionRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.AccountRecord;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.records.EnigmaEvaluationRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.MusicianProfileRecord;
import com.eh8s.eh8s.repository.interfaces.IEnrollmentRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_PLAN;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ENIGMA_EVALUATION;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;

/**
 * JOOQ persistence for enrollment tables.
 */
@Repository
public class EnrollmentRepository implements IEnrollmentRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public EnrollmentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Account insertAccount(Account account) {
    AccountRecord rec = dsl.newRecord(ACCOUNT, account);
    rec.changed(ACCOUNT.ID, false);
    rec.store();
    return rec.into(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MusicianProfile insertMusician(MusicianProfile profile) {
    MusicianProfileRecord rec = dsl.newRecord(MUSICIAN_PROFILE, profile);
    rec.changed(MUSICIAN_PROFILE.ID, false);
    rec.store();
    return rec.into(MusicianProfile.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<MusicianProfile> findMusicians() {
    return dsl.selectFrom(MUSICIAN_PROFILE).orderBy(MUSICIAN_PROFILE.ID).fetchInto(MusicianProfile.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<AcademyPlan> findPlan(Long planId) {
    return dsl.selectFrom(ACADEMY_PLAN).where(ACADEMY_PLAN.ID.eq(planId)).fetchOptionalInto(AcademyPlan.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AcademySubscription insertSubscription(AcademySubscription subscription) {
    AcademySubscriptionRecord rec = dsl.newRecord(ACADEMY_SUBSCRIPTION, subscription);
    rec.changed(ACADEMY_SUBSCRIPTION.ID, false);
    rec.store();
    return rec.into(AcademySubscription.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AcademySubscription> findSubscriptions() {
    return dsl.selectFrom(ACADEMY_SUBSCRIPTION)
        .orderBy(ACADEMY_SUBSCRIPTION.ID)
        .fetchInto(AcademySubscription.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EnigmaEvaluation insertEvaluation(EnigmaEvaluation evaluation) {
    EnigmaEvaluationRecord rec = dsl.newRecord(ENIGMA_EVALUATION, evaluation);
    rec.changed(ENIGMA_EVALUATION.ID, false);
    rec.store();
    dsl.update(MUSICIAN_PROFILE)
        .set(MUSICIAN_PROFILE.ENIGMA_SCORE, evaluation.getScore())
        .where(MUSICIAN_PROFILE.ID.eq(evaluation.getMusicianProfileId()))
        .execute();
    return rec.into(EnigmaEvaluation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<EnigmaEvaluation> findEvaluations() {
    return dsl.selectFrom(ENIGMA_EVALUATION)
        .orderBy(ENIGMA_EVALUATION.ID)
        .fetchInto(EnigmaEvaluation.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Account> findAccountByWallet(String walletPubkey) {
    return dsl.selectFrom(ACCOUNT)
        .where(ACCOUNT.WALLET_PUBKEY.eq(walletPubkey))
        .fetchOptionalInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Account> findAccountByEmail(String email) {
    return dsl.selectFrom(ACCOUNT)
        .where(ACCOUNT.EMAIL.eq(email))
        .fetchOptionalInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Account touchAccount(Long accountId) {
    dsl.update(ACCOUNT)
        .set(ACCOUNT.LAST_SEEN_AT, java.time.LocalDateTime.now())
        .where(ACCOUNT.ID.eq(accountId))
        .execute();
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOneInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<MusicianProfile> findMusicianByAccount(Long accountId) {
    return dsl.selectFrom(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .fetchOptionalInto(MusicianProfile.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Long> findEnigmaLevelId(int levelNumber) {
    return dsl.select(ENIGMA_LEVEL.ID)
        .from(ENIGMA_LEVEL)
        .where(ENIGMA_LEVEL.LEVEL_NUMBER.eq((byte) levelNumber))
        .fetchOptional(ENIGMA_LEVEL.ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Account> findAccounts() {
    return dsl.selectFrom(ACCOUNT).orderBy(ACCOUNT.ID).fetchInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Account updateLocation(Long accountId, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
    dsl.update(ACCOUNT)
        .set(ACCOUNT.LAST_LAT, latitude)
        .set(ACCOUNT.LAST_LNG, longitude)
        .set(ACCOUNT.LAST_GEO_AT, java.time.LocalDateTime.now())
        .set(ACCOUNT.LAST_SEEN_AT, java.time.LocalDateTime.now())
        .where(ACCOUNT.ID.eq(accountId))
        .execute();
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOneInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Account updateRole(Long accountId, String role) {
    dsl.update(ACCOUNT)
        .set(ACCOUNT.ROLE, role)
        .set(ACCOUNT.LAST_SEEN_AT, java.time.LocalDateTime.now())
        .where(ACCOUNT.ID.eq(accountId))
        .execute();
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOneInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Account updatePasswordHash(Long accountId, String passwordHash) {
    dsl.update(ACCOUNT)
        .set(ACCOUNT.PASSWORD_HASH, passwordHash)
        .where(ACCOUNT.ID.eq(accountId))
        .execute();
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOneInto(Account.class);
  }
}
