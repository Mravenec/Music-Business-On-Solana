package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ reads and writes for the five SPP inputs: creative ratings, concert set
 * check-ins, Enigma levels, and the per-cycle source rows used by close_cycle.
 */
public interface ISppInputsRepository {

  /**
   * Resolves the musician profile that belongs to an account.
   *
   * @param accountId account id from the JWT
   * @return profile id when the account is a musician
   */
  Optional<Long> findMusicianProfileId(Long accountId);

  /**
   * Checks band membership.
   *
   * @param bandId band id
   * @param musicianProfileId musician profile id
   * @return true when the musician is on the roster
   */
  boolean isMember(Long bandId, Long musicianProfileId);

  /**
   * Loads one rehearsal session.
   *
   * @param sessionId rehearsal session id
   * @return session when it exists
   */
  Optional<RehearsalSession> findSession(Long sessionId);

  /**
   * Loads one rehearsal check-in.
   *
   * @param sessionId rehearsal session id
   * @param musicianProfileId musician profile id
   * @return check-in when the musician attended
   */
  Optional<RehearsalCheckin> findCheckin(Long sessionId, Long musicianProfileId);

  /**
   * Checks whether the rater already rated this ratee for the session.
   *
   * @param sessionId rehearsal session id
   * @param raterProfileId rater musician profile id
   * @param rateeProfileId ratee musician profile id
   * @return true when a rating row exists
   */
  boolean ratingExists(Long sessionId, Long raterProfileId, Long rateeProfileId);

  /**
   * Inserts one creative rating.
   *
   * @param rating rating row (id ignored)
   * @return stored row with id
   */
  CreativeRating insertRating(CreativeRating rating);

  /**
   * Lists ratings for one rehearsal session.
   *
   * @param sessionId rehearsal session id
   * @return rating rows ordered by id
   */
  List<CreativeRating> findRatings(Long sessionId);

  /**
   * Loads one concert.
   *
   * @param concertId concert id
   * @return concert when it exists
   */
  Optional<Concert> findConcert(Long concertId);

  /**
   * Loads one set check-in.
   *
   * @param concertId concert id
   * @param musicianProfileId musician profile id
   * @return set check-in when the musician started a set
   */
  Optional<ConcertSetCheckin> findSetCheckin(Long concertId, Long musicianProfileId);

  /**
   * Inserts a set start check-in.
   *
   * @param checkin set check-in row (id ignored)
   * @return stored row with id
   */
  ConcertSetCheckin insertSetCheckin(ConcertSetCheckin checkin);

  /**
   * Writes set end time and minutes played.
   *
   * @param checkin row with id, setEndedAt and minutesPlayed
   * @return refreshed row
   */
  ConcertSetCheckin updateSetCheckinEnd(ConcertSetCheckin checkin);

  /**
   * Lists set check-ins for one concert.
   *
   * @param concertId concert id
   * @return set check-ins ordered by id
   */
  List<ConcertSetCheckin> findSetCheckins(Long concertId);

  /**
   * Reads the current Enigma level number (1..5) of a musician.
   *
   * @param musicianProfileId musician profile id
   * @return level number when the profile exists
   */
  Optional<Integer> findEnigmaLevel(Long musicianProfileId);

  /**
   * Lists rehearsal sessions of a band scheduled in [from, toExclusive).
   *
   * @param bandId band id
   * @param from inclusive start
   * @param toExclusive exclusive end
   * @return sessions ordered by id
   */
  List<RehearsalSession> findSessionsBetween(Long bandId, LocalDateTime from, LocalDateTime toExclusive);

  /**
   * Lists check-ins for a set of sessions.
   *
   * @param sessionIds rehearsal session ids
   * @return check-in rows
   */
  List<RehearsalCheckin> findCheckinsForSessions(List<Long> sessionIds);

  /**
   * Lists creative ratings for a set of sessions.
   *
   * @param sessionIds rehearsal session ids
   * @return rating rows
   */
  List<CreativeRating> findRatingsForSessions(List<Long> sessionIds);

  /**
   * Lists concerts that count toward one SPP cycle.
   *
   * @param cycleId SPP cycle id
   * @return concerts ordered by id
   */
  List<Concert> findConcertsByCycle(Long cycleId);

  /**
   * Lists set check-ins for a set of concerts.
   *
   * @param concertIds concert ids
   * @return set check-in rows
   */
  List<ConcertSetCheckin> findSetCheckinsForConcerts(List<Long> concertIds);

  /**
   * Lists the SPP variables with their weights in basis points.
   *
   * @return variable rows
   */
  List<SppVariable> findVariables();
}
