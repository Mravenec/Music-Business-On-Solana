package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import java.util.List;

/**
 * Agent tick use cases that append the event log and may create owner decisions.
 */
public interface IAgentEventService {

  /**
   * Lists agent event log rows newest first.
   *
   * @return events
   */
  List<AgentEventLog> events();

  /**
   * Runs a deterministic agent tick. Yellow/red create a pending owner decision;
   * green records the event only.
   *
   * @param agentId agent primary key
   * @param input event POJO carrying {@code eventType}, {@code summary}, optional {@code detail}
   *     and {@code semaphore} (agent default when null); other columns are ignored
   * @return stored event (with ownerDecisionId when created)
   * @throws org.springframework.web.server.ResponseStatusException 404 unknown agent, 400 missing
   *     eventType / summary or a semaphore other than green, yellow, red
   */
  AgentEventLog tick(Long agentId, AgentEventLog input);

  /**
   * Records an event produced by a real agent action (NEXUS evaluation, HARMONY match run, ATLAS
   * route) or a code-based ops tick. Same rules as {@link #tick(Long, AgentEventLog)}: yellow/red
   * open a pending owner decision.
   *
   * @param agentCode ops_agent code (for example NEXUS, HARMONY, ATLAS, STAGE)
   * @param eventType short event type
   * @param semaphore green, yellow, or red ({@code null} = the agent's current semaphore)
   * @param summary one-line summary (trimmed to 240 chars)
   * @param detail longer detail (trimmed to 1000 chars; {@code null} = summary)
   * @return stored event
   * @throws org.springframework.web.server.ResponseStatusException 404 unknown agent code
   */
  AgentEventLog record(
      String agentCode, String eventType, String semaphore, String summary, String detail);
}
