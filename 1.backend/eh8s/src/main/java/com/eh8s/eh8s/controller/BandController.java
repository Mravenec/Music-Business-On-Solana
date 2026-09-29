package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IBandController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMember;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import com.eh8s.eh8s.service.interfaces.IBandService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves band roster and SPP JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class BandController implements IBandController {

  private final IBandService bandService;

  /**
   * Creates the controller.
   *
   * @param bandService band use cases
   */
  public BandController(IBandService bandService) {
    this.bandService = bandService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bands")
  public List<Band> bands() {
    return bandService.bands();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/bands")
  public Band createBand(@RequestBody Band band) {
    return bandService.createBand(band);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bands/{bandId}/members")
  public List<BandMember> members(@PathVariable Long bandId) {
    return bandService.members(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/band-members")
  public BandMember addMember(@RequestBody BandMember member) {
    return bandService.addMember(member);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/spp-variables")
  public List<SppVariable> variables() {
    return bandService.variables();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bands/{bandId}/cycles")
  public List<SppCycle> cycles(@PathVariable Long bandId) {
    return bandService.cycles(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/spp-cycles")
  public SppCycle openCycle(@RequestBody SppCycle cycle) {
    return bandService.openCycle(cycle);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/spp-cycles/{cycleId}/scores")
  public List<SppMemberScore> scores(@PathVariable Long cycleId) {
    return bandService.scores(cycleId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bands/{bandId}/rehearsal-sessions")
  public List<RehearsalSession> sessions(@PathVariable Long bandId) {
    return bandService.sessions(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bands/{bandId}/rehearsals")
  public List<RehearsalSession> rehearsals(@PathVariable Long bandId) {
    return bandService.sessions(bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/rehearsal-sessions")
  public RehearsalSession createSession(@RequestBody RehearsalSession session) {
    return bandService.createSession(session);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/rehearsal-checkins")
  public RehearsalCheckin checkIn(@RequestBody RehearsalCheckin checkin) {
    return bandService.checkIn(checkin);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/spp-cycles/{cycleId}/close")
  public List<SppMemberScore> closeCycle(@PathVariable Long cycleId) {
    return bandService.closeCycle(cycleId);
  }
}
