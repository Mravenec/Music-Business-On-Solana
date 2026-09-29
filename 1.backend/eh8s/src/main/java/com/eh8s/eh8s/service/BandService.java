package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMember;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import com.eh8s.eh8s.repository.interfaces.IBandRepository;
import com.eh8s.eh8s.service.interfaces.IBandService;
import com.eh8s.eh8s.service.interfaces.ISppInputsService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Off-chain SPP. Close computes the five weighted variables from real inputs.
 */
@Service
public class BandService implements IBandService {

  static final int LATE_CUTOFF_MINUTES = 15;
  static final int SHARE_TOTAL_BPS = 10_000;

  private final IBandRepository bandRepository;
  private final ISppInputsService sppInputsService;

  /**
   * Creates the service.
   *
   * @param bandRepository band persistence
   * @param sppInputsService SPP inputs (skill capture + close computation)
   */
  public BandService(IBandRepository bandRepository, ISppInputsService sppInputsService) {
    this.bandRepository = bandRepository;
    this.sppInputsService = sppInputsService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Band> bands() {
    return bandRepository.findBands();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Band createBand(Band band) {
    if (band.getSppEnabled() == null) {
      band.setSppEnabled((byte) 0);
    }
    if (band.getBandType() == null || band.getBandType().isBlank()) {
      band.setBandType("seed");
    }
    return bandRepository.insertBand(band);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<BandMember> members(Long bandId) {
    return bandRepository.findMembers(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public BandMember addMember(BandMember member) {
    if (bandRepository.findMember(member.getBandId(), member.getMusicianProfileId()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Already a member of this band");
    }
    if (member.getJoinedAt() == null) {
      member.setJoinedAt(LocalDate.now());
    }
    if (member.getRoleInBand() == null || member.getRoleInBand().isBlank()) {
      member.setRoleInBand("member");
    }
    BandMember stored = bandRepository.insertMember(member);
    for (SppCycle cycle : bandRepository.findCycles(member.getBandId())) {
      if ("open".equalsIgnoreCase(cycle.getStatus())) {
        SppMemberScore score = new SppMemberScore();
        score.setSppCycleId(cycle.getId());
        score.setMusicianProfileId(member.getMusicianProfileId());
        score.setAttendancePoints(0);
        score.setPunctualityPoints(0);
        score.setCreativePoints(0);
        score.setSkillPoints(0);
        score.setConcertPoints(0);
        score.setTotalPoints(0);
        score.setShareBps(0);
        score.setEnigmaLevelStart(sppInputsService.currentEnigmaLevel(member.getMusicianProfileId()));
        bandRepository.insertScore(score);
      }
    }
    return stored;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppVariable> variables() {
    return bandRepository.findVariables();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppCycle> cycles(Long bandId) {
    return bandRepository.findCycles(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SppCycle openCycle(SppCycle cycle) {
    if (cycle.getStatus() == null || cycle.getStatus().isBlank()) {
      cycle.setStatus("open");
    }
    if (cycle.getStartedAt() == null) {
      cycle.setStartedAt(LocalDate.now());
    }
    if (cycle.getIntendedInstruction() == null || cycle.getIntendedInstruction().isBlank()) {
      cycle.setIntendedInstruction("update_spp_weights");
    }
    SppCycle stored = bandRepository.insertCycle(cycle);
    for (BandMember member : bandRepository.findMembers(cycle.getBandId())) {
      SppMemberScore score = new SppMemberScore();
      score.setSppCycleId(stored.getId());
      score.setMusicianProfileId(member.getMusicianProfileId());
      score.setAttendancePoints(0);
      score.setPunctualityPoints(0);
      score.setCreativePoints(0);
      score.setSkillPoints(0);
      score.setConcertPoints(0);
      score.setTotalPoints(0);
      score.setShareBps(0);
      score.setEnigmaLevelStart(sppInputsService.currentEnigmaLevel(member.getMusicianProfileId()));
      bandRepository.insertScore(score);
    }
    return stored;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppMemberScore> scores(Long cycleId) {
    return bandRepository.findScores(cycleId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<RehearsalSession> sessions(Long bandId) {
    return bandRepository.findSessions(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RehearsalSession createSession(RehearsalSession session) {
    if (session.getScheduledAt() == null) {
      session.setScheduledAt(LocalDateTime.now().plusDays(1));
    }
    return bandRepository.insertSession(session);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RehearsalCheckin checkIn(RehearsalCheckin checkin) {
    if (bandRepository
        .findCheckin(checkin.getRehearsalSessionId(), checkin.getMusicianProfileId())
        .isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Already checked in for this rehearsal session");
    }
    if (checkin.getArrivedAt() == null) {
      checkin.setArrivedAt(LocalDateTime.now());
    }
    int late = checkin.getLateMinutes() == null ? 0 : checkin.getLateMinutes();
    checkin.setLateMinutes(late);
    if (checkin.getAttendancePoints() == null) {
      checkin.setAttendancePoints(10);
    }
    if (late > LATE_CUTOFF_MINUTES) {
      checkin.setPunctualityPoints(0);
    } else if (checkin.getPunctualityPoints() == null) {
      checkin.setPunctualityPoints(10);
    }
    return bandRepository.insertCheckin(checkin);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SppMemberScore> closeCycle(Long cycleId) {
    SppCycle cycle =
        bandRepository
            .findCycle(cycleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown cycle"));
    if ("closed".equalsIgnoreCase(cycle.getStatus())) {
      return bandRepository.findScores(cycleId);
    }
    LocalDate closeDate = cycle.getClosedAt() == null ? LocalDate.now() : cycle.getClosedAt();
    List<SppMemberScore> scores = new ArrayList<>(bandRepository.findScores(cycleId));
    sppInputsService.computeCycleScores(cycle, scores, closeDate);
    applyShareBps(scores);
    bandRepository.updateScores(scores);
    cycle.setStatus("closed");
    cycle.setClosedAt(closeDate);
    cycle.setIntendedInstruction("update_spp_weights");
    bandRepository.updateCycle(cycle);
    return bandRepository.findScores(cycleId);
  }

  /**
   * Writes share_bps from total_points so the cycle sums to 10000.
   *
   * @param scores mutable score rows
   */
  static void applyShareBps(List<SppMemberScore> scores) {
    int sum = 0;
    for (SppMemberScore score : scores) {
      int total = pointsOf(score);
      score.setTotalPoints(total);
      sum += total;
    }
    if (scores.isEmpty()) {
      return;
    }
    if (sum <= 0) {
      for (SppMemberScore score : scores) {
        score.setShareBps(0);
      }
      return;
    }
    int allocated = 0;
    for (int i = 0; i < scores.size(); i++) {
      SppMemberScore score = scores.get(i);
      if (i == scores.size() - 1) {
        score.setShareBps(SHARE_TOTAL_BPS - allocated);
      } else {
        int bps = (int) Math.round(score.getTotalPoints() * (double) SHARE_TOTAL_BPS / sum);
        score.setShareBps(bps);
        allocated += bps;
      }
    }
  }

  private static int pointsOf(SppMemberScore score) {
    if (score.getTotalPoints() != null && score.getTotalPoints() > 0) {
      return score.getTotalPoints();
    }
    return nz(score.getAttendancePoints())
        + nz(score.getPunctualityPoints())
        + nz(score.getCreativePoints())
        + nz(score.getSkillPoints())
        + nz(score.getConcertPoints());
  }

  private static int nz(Integer value) {
    return value == null ? 0 : value;
  }
}
