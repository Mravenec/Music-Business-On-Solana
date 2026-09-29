package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMember;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import java.util.List;

/**
 * HTTP contract for bands and SPP resources.
 */
public interface IBandController {

  /**
   * @return every band
   */
  List<Band> bands();

  /**
   * Creates a band.
   *
   * @param band body
   * @return stored band
   */
  Band createBand(Band band);

  /**
   * @param bandId band primary key
   * @return members
   */
  List<BandMember> members(Long bandId);

  /**
   * Adds a band member.
   *
   * @param member body
   * @return stored member
   */
  BandMember addMember(BandMember member);

  /**
   * @return SPP variables
   */
  List<SppVariable> variables();

  /**
   * @param bandId band primary key
   * @return cycles
   */
  List<SppCycle> cycles(Long bandId);

  /**
   * Opens an SPP cycle.
   *
   * @param cycle body
   * @return stored cycle
   */
  SppCycle openCycle(SppCycle cycle);

  /**
   * @param cycleId cycle primary key
   * @return scores
   */
  List<SppMemberScore> scores(Long cycleId);

  /**
   * @param bandId band primary key
   * @return rehearsal sessions
   */
  List<RehearsalSession> sessions(Long bandId);

  /**
   * Alias for {@link #sessions(Long)} at {@code /bands/{id}/rehearsals}.
   *
   * @param bandId band primary key
   * @return rehearsal sessions
   */
  List<RehearsalSession> rehearsals(Long bandId);

  /**
   * Creates a rehearsal session.
   *
   * @param session body
   * @return stored session
   */
  RehearsalSession createSession(RehearsalSession session);

  /**
   * @param checkin rehearsal check-in
   * @return stored row
   */
  RehearsalCheckin checkIn(RehearsalCheckin checkin);

  /**
   * @param cycleId cycle to close
   * @return updated scores
   */
  List<SppMemberScore> closeCycle(Long cycleId);
}
