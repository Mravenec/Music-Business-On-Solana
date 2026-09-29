package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.INexusController;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.service.interfaces.INexusService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves NEXUS Score Enigma requests and history.
 */
@RestController
@RequestMapping("/api/nexus")
public class NexusController implements INexusController {

  private final INexusService nexusService;

  /**
   * Creates the controller.
   *
   * @param nexusService NEXUS use cases
   */
  public NexusController(INexusService nexusService) {
    this.nexusService = nexusService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/status")
  public Map<String, Object> status() {
    return nexusService.status();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/evaluations")
  public Map<String, Object> evaluate(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody EnigmaEvaluation input) {
    return nexusService.evaluate(accountId(principal), input);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/evaluations")
  public List<Map<String, Object>> evaluations(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(required = false) Long musicianProfileId) {
    return nexusService.evaluations(accountId(principal), musicianProfileId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/evaluations/{evaluationId}")
  public Map<String, Object> evaluation(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long evaluationId) {
    return nexusService.evaluation(accountId(principal), evaluationId);
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
