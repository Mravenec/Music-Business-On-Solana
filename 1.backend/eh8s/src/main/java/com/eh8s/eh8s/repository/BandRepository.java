package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMember;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.BandMemberRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.BandRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.RehearsalCheckinRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.RehearsalSessionRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.SppCycleRecord;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.SppMemberScoreRecord;
import com.eh8s.eh8s.repository.interfaces.IBandRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.REHEARSAL_CHECKIN;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.REHEARSAL_SESSION;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_CYCLE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_MEMBER_SCORE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.SPP_VARIABLE;

/**
 * JOOQ persistence for band and SPP tables.
 */
@Repository
public class BandRepository implements IBandRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public BandRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Band> findBands() {
    return dsl.selectFrom(BAND).orderBy(BAND.ID).fetchInto(Band.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Band insertBand(Band band) {
    BandRecord rec = dsl.newRecord(BAND, band);
    rec.changed(BAND.ID, false);
    rec.store();
    return rec.into(Band.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<BandMember> findMembers(Long bandId) {
    return dsl.selectFrom(BAND_MEMBER)
        .where(BAND_MEMBER.BAND_ID.eq(bandId))
        .orderBy(BAND_MEMBER.ID)
        .fetchInto(BandMember.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public BandMember insertMember(BandMember member) {
    BandMemberRecord rec = dsl.newRecord(BAND_MEMBER, member);
    rec.changed(BAND_MEMBER.ID, false);
    rec.store();
    return rec.into(BandMember.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<BandMember> findMember(Long bandId, Long musicianProfileId) {
    return dsl.selectFrom(BAND_MEMBER)
        .where(BAND_MEMBER.BAND_ID.eq(bandId).and(BAND_MEMBER.MUSICIAN_PROFILE_ID.eq(musicianProfileId)))
        .fetchOptionalInto(BandMember.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppVariable> findVariables() {
    return dsl.selectFrom(SPP_VARIABLE).orderBy(SPP_VARIABLE.ID).fetchInto(SppVariable.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppCycle> findCycles(Long bandId) {
    return dsl.selectFrom(SPP_CYCLE)
        .where(SPP_CYCLE.BAND_ID.eq(bandId))
        .orderBy(SPP_CYCLE.ID)
        .fetchInto(SppCycle.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<SppCycle> findCycle(Long cycleId) {
    return dsl.selectFrom(SPP_CYCLE).where(SPP_CYCLE.ID.eq(cycleId)).fetchOptionalInto(SppCycle.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SppCycle insertCycle(SppCycle cycle) {
    SppCycleRecord rec = dsl.newRecord(SPP_CYCLE, cycle);
    rec.changed(SPP_CYCLE.ID, false);
    rec.store();
    return rec.into(SppCycle.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SppMemberScore insertScore(SppMemberScore score) {
    SppMemberScoreRecord rec = dsl.newRecord(SPP_MEMBER_SCORE, score);
    rec.changed(SPP_MEMBER_SCORE.ID, false);
    rec.store();
    return rec.into(SppMemberScore.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppMemberScore> findScores(Long cycleId) {
    return dsl.selectFrom(SPP_MEMBER_SCORE)
        .where(SPP_MEMBER_SCORE.SPP_CYCLE_ID.eq(cycleId))
        .orderBy(SPP_MEMBER_SCORE.ID)
        .fetchInto(SppMemberScore.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateScores(List<SppMemberScore> scores) {
    for (SppMemberScore score : scores) {
      dsl.update(SPP_MEMBER_SCORE)
          .set(SPP_MEMBER_SCORE.ATTENDANCE_POINTS, score.getAttendancePoints())
          .set(SPP_MEMBER_SCORE.PUNCTUALITY_POINTS, score.getPunctualityPoints())
          .set(SPP_MEMBER_SCORE.CREATIVE_POINTS, score.getCreativePoints())
          .set(SPP_MEMBER_SCORE.SKILL_POINTS, score.getSkillPoints())
          .set(SPP_MEMBER_SCORE.CONCERT_POINTS, score.getConcertPoints())
          .set(SPP_MEMBER_SCORE.ENIGMA_LEVEL_END, score.getEnigmaLevelEnd())
          .set(SPP_MEMBER_SCORE.TOTAL_POINTS, score.getTotalPoints())
          .set(SPP_MEMBER_SCORE.SHARE_BPS, score.getShareBps())
          .where(SPP_MEMBER_SCORE.ID.eq(score.getId()))
          .execute();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SppCycle updateCycle(SppCycle cycle) {
    dsl.update(SPP_CYCLE)
        .set(SPP_CYCLE.STATUS, cycle.getStatus())
        .set(SPP_CYCLE.CLOSED_AT, cycle.getClosedAt())
        .set(SPP_CYCLE.INTENDED_INSTRUCTION, cycle.getIntendedInstruction())
        .where(SPP_CYCLE.ID.eq(cycle.getId()))
        .execute();
    return findCycle(cycle.getId()).orElse(cycle);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RehearsalSession> findSessions(Long bandId) {
    return dsl.selectFrom(REHEARSAL_SESSION)
        .where(REHEARSAL_SESSION.BAND_ID.eq(bandId))
        .orderBy(REHEARSAL_SESSION.ID)
        .fetchInto(RehearsalSession.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RehearsalSession insertSession(RehearsalSession session) {
    RehearsalSessionRecord rec = dsl.newRecord(REHEARSAL_SESSION, session);
    rec.changed(REHEARSAL_SESSION.ID, false);
    rec.store();
    return rec.into(RehearsalSession.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RehearsalCheckin insertCheckin(RehearsalCheckin checkin) {
    RehearsalCheckinRecord rec = dsl.newRecord(REHEARSAL_CHECKIN, checkin);
    rec.changed(REHEARSAL_CHECKIN.ID, false);
    rec.store();
    return rec.into(RehearsalCheckin.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<RehearsalCheckin> findCheckin(Long rehearsalSessionId, Long musicianProfileId) {
    return dsl.selectFrom(REHEARSAL_CHECKIN)
        .where(
            REHEARSAL_CHECKIN
                .REHEARSAL_SESSION_ID
                .eq(rehearsalSessionId)
                .and(REHEARSAL_CHECKIN.MUSICIAN_PROFILE_ID.eq(musicianProfileId)))
        .fetchOptionalInto(RehearsalCheckin.class);
  }
}
