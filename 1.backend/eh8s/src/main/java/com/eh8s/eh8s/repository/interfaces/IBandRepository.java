package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMember;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for bands, rehearsals, and SPP cycle rows.
 */
public interface IBandRepository {

  /**
   * @return every band
   */
  List<Band> findBands();

  /**
   * Inserts a band.
   *
   * @param band row
   * @return stored band
   */
  Band insertBand(Band band);

  /**
   * @param bandId band primary key
   * @return members of that band
   */
  List<BandMember> findMembers(Long bandId);

  /**
   * Inserts a band member.
   *
   * @param member row
   * @return stored member
   */
  BandMember insertMember(BandMember member);

  /**
   * Finds an existing membership row (unique key {@code uk_band_member}).
   *
   * @param bandId band primary key
   * @param musicianProfileId musician profile primary key
   * @return membership if present
   */
  Optional<BandMember> findMember(Long bandId, Long musicianProfileId);

  /**
   * @return the five SPP weight variables
   */
  List<SppVariable> findVariables();

  /**
   * @param bandId band primary key
   * @return SPP cycles for the band
   */
  List<SppCycle> findCycles(Long bandId);

  /**
   * @param cycleId cycle primary key
   * @return the cycle, or empty
   */
  Optional<SppCycle> findCycle(Long cycleId);

  /**
   * Inserts an SPP cycle.
   *
   * @param cycle row
   * @return stored cycle
   */
  SppCycle insertCycle(SppCycle cycle);

  /**
   * Inserts a zeroed SPP member score row.
   *
   * @param score row
   * @return stored score
   */
  SppMemberScore insertScore(SppMemberScore score);

  /**
   * @param cycleId cycle primary key
   * @return member scores for that cycle
   */
  List<SppMemberScore> findScores(Long cycleId);

  /**
   * Persists the five variable points, ending Enigma level, total points, and share basis points.
   *
   * @param scores rows to update
   */
  void updateScores(List<SppMemberScore> scores);

  /**
   * Marks a cycle closed.
   *
   * @param cycle cycle to persist
   * @return the stored cycle
   */
  SppCycle updateCycle(SppCycle cycle);

  /**
   * @param bandId band primary key
   * @return rehearsal sessions
   */
  List<RehearsalSession> findSessions(Long bandId);

  /**
   * Inserts a rehearsal session.
   *
   * @param session row
   * @return stored session
   */
  RehearsalSession insertSession(RehearsalSession session);

  /**
   * Stores one rehearsal check-in.
   *
   * @param checkin row to insert
   * @return the stored row
   */
  RehearsalCheckin insertCheckin(RehearsalCheckin checkin);

  /**
   * Finds an existing check-in row (unique key {@code uk_checkin}).
   *
   * @param rehearsalSessionId rehearsal session primary key
   * @param musicianProfileId musician profile primary key
   * @return check-in if present
   */
  Optional<RehearsalCheckin> findCheckin(Long rehearsalSessionId, Long musicianProfileId);
}
