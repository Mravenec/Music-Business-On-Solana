package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;

/**
 * HTTP contract for SPP inputs: peer creative ratings after rehearsals and concert set check-ins.
 * The acting musician is always the JWT account, never a body field.
 */
public interface ISppInputsController {

  /**
   * Lists creative ratings for one rehearsal session.
   *
   * @param sessionId rehearsal session id
   * @return rating rows
   */
  List<CreativeRating> ratings(Long sessionId);

  /**
   * Rates a bandmate for one rehearsal (score 1..5).
   *
   * @param principal JWT principal
   * @param sessionId rehearsal session id
   * @param rating body with rateeProfileId and score
   * @return stored rating
   */
  CreativeRating rate(JwtPrincipal principal, Long sessionId, CreativeRating rating);

  /**
   * Lists set check-ins for one concert.
   *
   * @param concertId concert id
   * @return set check-in rows
   */
  List<ConcertSetCheckin> setCheckins(Long concertId);

  /**
   * Starts the signed-in musician's set.
   *
   * @param principal JWT principal
   * @param concertId concert id
   * @param checkin optional body with setStartedAt
   * @return stored set check-in
   */
  ConcertSetCheckin startSet(JwtPrincipal principal, Long concertId, ConcertSetCheckin checkin);

  /**
   * Ends the signed-in musician's set and stores minutes played.
   *
   * @param principal JWT principal
   * @param concertId concert id
   * @param checkin optional body with setEndedAt
   * @return updated set check-in
   */
  ConcertSetCheckin endSet(JwtPrincipal principal, Long concertId, ConcertSetCheckin checkin);
}
