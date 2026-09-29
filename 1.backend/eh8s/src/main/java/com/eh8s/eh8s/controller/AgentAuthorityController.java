package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IAgentAuthorityController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.service.interfaces.IAgentAuthorityService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves owner agent authorization and agent-only Enigma level build/confirm JSON.
 */
@RestController
@RequestMapping("/api")
public class AgentAuthorityController implements IAgentAuthorityController {

  private final IAgentAuthorityService agentAuthorityService;

  /**
   * Creates the controller.
   *
   * @param agentAuthorityService agent authority use cases
   */
  public AgentAuthorityController(IAgentAuthorityService agentAuthorityService) {
    this.agentAuthorityService = agentAuthorityService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/owner/agents")
  public Map<String, Object> roster(@RequestParam(required = false) String walletPubkey) {
    return agentAuthorityService.roster(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/owner/agents/authorize/build")
  public Map<String, Object> buildAuthorize(@RequestBody Map<String, Object> body) {
    return agentAuthorityService.buildAuthorize(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "agentCode"),
        HttpBody.raw(body, "agentWalletPubkey"),
        HttpBody.intValue(body, "permissions"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/owner/agents/authorize/confirm")
  public AgentAuthority confirmAuthorize(@RequestBody Map<String, Object> body) {
    return agentAuthorityService.confirmAuthorize(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "agentCode"),
        HttpBody.raw(body, "agentWalletPubkey"),
        HttpBody.intValue(body, "permissions"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/agents/level/musicians")
  public Map<String, Object> levelTargets(@RequestParam(required = false) String walletPubkey) {
    return agentAuthorityService.levelTargets(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/agents/level/build")
  public Map<String, Object> buildLevel(@RequestBody Map<String, Object> body) {
    return agentAuthorityService.buildLevel(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.longValue(body, "musicianProfileId"),
        HttpBody.intValue(body, "newLevel"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/agents/level/confirm")
  public MusicianLevelChange confirmLevel(@RequestBody Map<String, Object> body) {
    return agentAuthorityService.confirmLevel(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.longValue(body, "musicianProfileId"),
        HttpBody.intValue(body, "newLevel"),
        HttpBody.raw(body, "txSignature"));
  }
}
