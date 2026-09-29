package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import java.time.LocalDate;
import java.util.List;

/**
 * SPP inputs: peer creative ratings, concert set check-ins, Enigma skill capture, and
 * the close_cycle computation of all five weighted variables.
 */
public interface ISppInputsService {

  /**
   * Records one creative rating from the signed-in musician to a bandmate. The rater must be on
   * the band roster and checked in to that rehearsal.
   *
   * @param accountId account id from the JWT (resolves the rater profile)
   * @param sessionId rehearsal session id
   * @param rating body with rateeProfileId and score 1..5
   * @return stored rating
   */
  CreativeRating rate(Long accountId, Long sessionId, CreativeRating rating);

  /**
   * Lists creative ratings for one rehearsal session.
   *
   * @param sessionId rehearsal session id
   * @return rating rows
   */
  List<CreativeRating> ratings(Long sessionId);

  /**
   * Starts the signed-in musician's set at a concert.
   *
   * @param accountId account id from the JWT
   * @param concertId concert id
   * @param checkin optional body with setStartedAt (defaults to now)
   * @return stored set check-in
   */
  ConcertSetCheckin startSet(Long accountId, Long concertId, ConcertSetCheckin checkin);

  /**
   * Ends the signed-in musician's set and stores minutes played (capped at show length).
   *
   * @param accountId account id from the JWT
   * @param concertId concert id
   * @param checkin optional body with setEndedAt (defaults to now)
   * @return updated set check-in
   */
  ConcertSetCheckin endSet(Long accountId, Long concertId, ConcertSetCheckin checkin);

  /**
   * Lists set check-ins for one concert.
   *
   * @param concertId concert id
   * @return set check-in rows
   */
  List<ConcertSetCheckin> setCheckins(Long concertId);

  /**
   * Reads the current Enigma level number of a musician (used to capture skill at cycle open).
   *
   * @param musicianProfileId musician profile id
   * @return level number, or null when the profile is unknown
   */
  Byte currentEnigmaLevel(Long musicianProfileId);

  /**
   * Fills the five variable points, ending Enigma level, and total points on each score row from
   * real inputs (check-ins, ratings, Enigma delta, concert minutes) weighted by spp_variable.
   * Rows are left untouched when the cycle has no inputs at all (legacy seeded cycles).
   *
   * @param cycle cycle being closed
   * @param scores mutable score rows of that cycle
   * @param closeDate last day included in the cycle
   * @return true when points were computed from inputs
   */
  boolean computeCycleScores(SppCycle cycle, List<SppMemberScore> scores, LocalDate closeDate);
}
