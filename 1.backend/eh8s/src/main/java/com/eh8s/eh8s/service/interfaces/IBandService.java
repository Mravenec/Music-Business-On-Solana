package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMember;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import java.util.List;

/**
 * Band roster and off-chain SPP cycle use cases.
 */
public interface IBandService {

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
   * @return members of that band
   */
  List<BandMember> members(Long bandId);

  /**
   * Adds a musician to a band.
   *
   * @param member body
   * @return stored member
   */
  BandMember addMember(BandMember member);

  /**
   * @return SPP weight catalog (sums to 10000 bps)
   */
  List<SppVariable> variables();

  /**
   * @param bandId band primary key
   * @return cycles for the band
   */
  List<SppCycle> cycles(Long bandId);

  /**
   * Opens an SPP cycle and seeds zero score rows for current members, capturing each member's
   * starting Enigma level for the skill variable.
   *
   * @param cycle body
   * @return stored cycle
   */
  SppCycle openCycle(SppCycle cycle);

  /**
   * @param cycleId cycle primary key
   * @return member scores
   */
  List<SppMemberScore> scores(Long cycleId);

  /**
   * @param bandId band primary key
   * @return rehearsal sessions
   */
  List<RehearsalSession> sessions(Long bandId);

  /**
   * Schedules a rehearsal session.
   *
   * @param session body
   * @return stored session
   */
  RehearsalSession createSession(RehearsalSession session);

  /**
   * Records a rehearsal check-in. Late over 15 minutes yields 0 punctuality.
   *
   * @param checkin incoming check-in
   * @return the stored row
   */
  RehearsalCheckin checkIn(RehearsalCheckin checkin);

  /**
   * Closes a cycle: computes attendance, punctuality, creative, skill, and concert points from
   * real inputs (weights from spp_variable), then writes share_bps that sum to 10000. Closing an
   * already closed cycle returns the stored snapshot unchanged.
   *
   * @param cycleId cycle primary key
   * @return updated scores
   */
  List<SppMemberScore> closeCycle(Long cycleId);
}
