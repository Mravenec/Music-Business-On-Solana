package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.ConcertSetCheckinRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.CreativeRatingRecord;
import com.eh8s.eh8s.repository.interfaces.ISppInputsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SET_CHECKIN;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CREATIVE_RATING;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.REHEARSAL_CHECKIN;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.REHEARSAL_SESSION;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_VARIABLE;

/**
 * JOOQ persistence for SPP inputs (creative ratings, concert set check-ins, Enigma levels).
 */
@Repository
public class SppInputsRepository implements ISppInputsRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public SppInputsRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Long> findMusicianProfileId(Long accountId) {
    return dsl.select(MUSICIAN_PROFILE.ID)
        .from(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .fetchOptional(MUSICIAN_PROFILE.ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMember(Long bandId, Long musicianProfileId) {
    return dsl.fetchExists(
        BAND_MEMBER,
        BAND_MEMBER.BAND_ID.eq(bandId).and(BAND_MEMBER.MUSICIAN_PROFILE_ID.eq(musicianProfileId)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<RehearsalSession> findSession(Long sessionId) {
    return dsl.selectFrom(REHEARSAL_SESSION)
        .where(REHEARSAL_SESSION.ID.eq(sessionId))
        .fetchOptionalInto(RehearsalSession.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<RehearsalCheckin> findCheckin(Long sessionId, Long musicianProfileId) {
    return dsl.selectFrom(REHEARSAL_CHECKIN)
        .where(
            REHEARSAL_CHECKIN
                .REHEARSAL_SESSION_ID
                .eq(sessionId)
                .and(REHEARSAL_CHECKIN.MUSICIAN_PROFILE_ID.eq(musicianProfileId)))
        .fetchOptionalInto(RehearsalCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean ratingExists(Long sessionId, Long raterProfileId, Long rateeProfileId) {
    return dsl.fetchExists(
        CREATIVE_RATING,
        CREATIVE_RATING
            .REHEARSAL_SESSION_ID
            .eq(sessionId)
            .and(CREATIVE_RATING.RATER_PROFILE_ID.eq(raterProfileId))
            .and(CREATIVE_RATING.RATEE_PROFILE_ID.eq(rateeProfileId)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CreativeRating insertRating(CreativeRating rating) {
    CreativeRatingRecord rec = dsl.newRecord(CREATIVE_RATING, rating);
    rec.changed(CREATIVE_RATING.ID, false);
    rec.changed(CREATIVE_RATING.CREATED_AT, false);
    rec.store();
    rec.refresh();
    return rec.into(CreativeRating.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CreativeRating> findRatings(Long sessionId) {
    return dsl.selectFrom(CREATIVE_RATING)
        .where(CREATIVE_RATING.REHEARSAL_SESSION_ID.eq(sessionId))
        .orderBy(CREATIVE_RATING.ID)
        .fetchInto(CreativeRating.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Concert> findConcert(Long concertId) {
    return dsl.selectFrom(CONCERT).where(CONCERT.ID.eq(concertId)).fetchOptionalInto(Concert.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<ConcertSetCheckin> findSetCheckin(Long concertId, Long musicianProfileId) {
    return dsl.selectFrom(CONCERT_SET_CHECKIN)
        .where(
            CONCERT_SET_CHECKIN
                .CONCERT_ID
                .eq(concertId)
                .and(CONCERT_SET_CHECKIN.MUSICIAN_PROFILE_ID.eq(musicianProfileId)))
        .fetchOptionalInto(ConcertSetCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConcertSetCheckin insertSetCheckin(ConcertSetCheckin checkin) {
    ConcertSetCheckinRecord rec = dsl.newRecord(CONCERT_SET_CHECKIN, checkin);
    rec.changed(CONCERT_SET_CHECKIN.ID, false);
    rec.store();
    return rec.into(ConcertSetCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConcertSetCheckin updateSetCheckinEnd(ConcertSetCheckin checkin) {
    dsl.update(CONCERT_SET_CHECKIN)
        .set(CONCERT_SET_CHECKIN.SET_ENDED_AT, checkin.getSetEndedAt())
        .set(CONCERT_SET_CHECKIN.MINUTES_PLAYED, checkin.getMinutesPlayed())
        .where(CONCERT_SET_CHECKIN.ID.eq(checkin.getId()))
        .execute();
    return dsl.selectFrom(CONCERT_SET_CHECKIN)
        .where(CONCERT_SET_CHECKIN.ID.eq(checkin.getId()))
        .fetchOneInto(ConcertSetCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSetCheckin> findSetCheckins(Long concertId) {
    return dsl.selectFrom(CONCERT_SET_CHECKIN)
        .where(CONCERT_SET_CHECKIN.CONCERT_ID.eq(concertId))
        .orderBy(CONCERT_SET_CHECKIN.ID)
        .fetchInto(ConcertSetCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Integer> findEnigmaLevel(Long musicianProfileId) {
    return dsl.select(ENIGMA_LEVEL.LEVEL_NUMBER)
        .from(MUSICIAN_PROFILE)
        .join(ENIGMA_LEVEL)
        .on(ENIGMA_LEVEL.ID.eq(MUSICIAN_PROFILE.ENIGMA_LEVEL_ID))
        .where(MUSICIAN_PROFILE.ID.eq(musicianProfileId))
        .fetchOptional(ENIGMA_LEVEL.LEVEL_NUMBER)
        .map(Byte::intValue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RehearsalSession> findSessionsBetween(
      Long bandId, LocalDateTime from, LocalDateTime toExclusive) {
    return dsl.selectFrom(REHEARSAL_SESSION)
        .where(REHEARSAL_SESSION.BAND_ID.eq(bandId))
        .and(REHEARSAL_SESSION.SCHEDULED_AT.ge(from))
        .and(REHEARSAL_SESSION.SCHEDULED_AT.lt(toExclusive))
        .orderBy(REHEARSAL_SESSION.ID)
        .fetchInto(RehearsalSession.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RehearsalCheckin> findCheckinsForSessions(List<Long> sessionIds) {
    if (sessionIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(REHEARSAL_CHECKIN)
        .where(REHEARSAL_CHECKIN.REHEARSAL_SESSION_ID.in(sessionIds))
        .fetchInto(RehearsalCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CreativeRating> findRatingsForSessions(List<Long> sessionIds) {
    if (sessionIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(CREATIVE_RATING)
        .where(CREATIVE_RATING.REHEARSAL_SESSION_ID.in(sessionIds))
        .fetchInto(CreativeRating.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Concert> findConcertsByCycle(Long cycleId) {
    return dsl.selectFrom(CONCERT)
        .where(CONCERT.SPP_CYCLE_ID.eq(cycleId))
        .orderBy(CONCERT.ID)
        .fetchInto(Concert.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSetCheckin> findSetCheckinsForConcerts(List<Long> concertIds) {
    if (concertIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(CONCERT_SET_CHECKIN)
        .where(CONCERT_SET_CHECKIN.CONCERT_ID.in(concertIds))
        .fetchInto(ConcertSetCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppVariable> findVariables() {
    return dsl.selectFrom(SPP_VARIABLE).orderBy(SPP_VARIABLE.ID).fetchInto(SppVariable.class);
  }
}
