package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.repository.interfaces.IAgentEventRepository;
import com.eh8s.eh8s.service.interfaces.IAgentEventService;
import com.eh8s.eh8s.service.interfaces.ISlackBridgeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Agent event log. Real agent actions (NEXUS, HARMONY, ATLAS) call {@link #record}; the tick
 * endpoint stays for manual ops notes. Yellow / red events open an owner decision that is sent to
 * Slack with Approve / Reject buttons.
 */
@Service
public class AgentEventService implements IAgentEventService {

  private static final Set<String> SEMAPHORES = Set.of("green", "yellow", "red");

  private final IAgentEventRepository agentEventRepository;
  private final ISlackBridgeService slackBridgeService;

  /**
   * Creates the service.
   *
   * @param agentEventRepository event persistence
   * @param slackBridgeService Slack bridge for escalated decisions
   */
  public AgentEventService(
      IAgentEventRepository agentEventRepository, ISlackBridgeService slackBridgeService) {
    this.agentEventRepository = agentEventRepository;
    this.slackBridgeService = slackBridgeService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<AgentEventLog> events() {
    return agentEventRepository.findAll();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AgentEventLog tick(Long agentId, AgentEventLog input) {
    OpsAgent agent =
        agentEventRepository
            .findAgent(agentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown agent"));
    AgentEventLog in = input == null ? new AgentEventLog() : input;
    return append(agent, in.getEventType(), in.getSemaphore(), in.getSummary(), in.getDetail());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AgentEventLog record(
      String agentCode, String eventType, String semaphore, String summary, String detail) {
    OpsAgent agent =
        agentEventRepository
            .findAgentByCode(agentCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown agent"));
    return append(agent, eventType, semaphore, summary, detail);
  }

  private AgentEventLog append(
      OpsAgent agent, String rawType, String rawSemaphore, String rawSummary, String rawDetail) {
    if (rawType == null || rawSummary == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType and summary are required");
    }

    String eventType = rawType.trim();
    String summary = rawSummary.trim();
    String detail = rawDetail == null ? summary : rawDetail.trim();
    if (eventType.isEmpty() || summary.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType and summary are required");
    }
    if (summary.length() > 240) {
      summary = summary.substring(0, 240);
    }
    if (detail.length() > 1000) {
      detail = detail.substring(0, 1000);
    }

    String semaphore =
        rawSemaphore == null ? agent.getSemaphore() : rawSemaphore.trim().toLowerCase();
    if (!SEMAPHORES.contains(semaphore)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semaphore must be green, yellow, or red");
    }

    Long decisionId = null;
    if ("yellow".equals(semaphore) || "red".equals(semaphore)) {
      OwnerDecision decision = new OwnerDecision();
      decision.setOpsAgentId(agent.getId());
      decision.setTitle(summary.length() > 160 ? summary.substring(0, 160) : summary);
      decision.setSemaphore(semaphore);
      decision.setPayload(detail.length() > 500 ? detail.substring(0, 500) : detail);
      decision.setStatus("pending");
      decision = agentEventRepository.insertDecision(decision);
      decisionId = decision.getId();
      slackBridgeService.notifyDecision(decision);
    }

    AgentEventLog event = new AgentEventLog();
    event.setOpsAgentId(agent.getId());
    event.setEventType(eventType);
    event.setSemaphore(semaphore);
    event.setSummary(summary);
    event.setDetail(detail);
    event.setOwnerDecisionId(decisionId);
    event.setCreatedAt(LocalDateTime.now());
    return agentEventRepository.insertEvent(event);
  }
}
