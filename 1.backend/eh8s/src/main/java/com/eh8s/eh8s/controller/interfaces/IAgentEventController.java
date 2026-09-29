package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for agent event ticks.
 */
public interface IAgentEventController {

  /**
   * Lists agent events newest first.
   *
   * @return events
   */
  List<AgentEventLog> events();

  /**
   * Appends an event for one agent.
   *
   * @param id agent id
   * @param input event POJO ({@code eventType}, {@code summary}, optional {@code detail},
   *     {@code semaphore})
   * @return inserted event
   */
  AgentEventLog tick(Long id, AgentEventLog input);

  /**
   * Convenience tick using {@code agentCode} in the body (defaults STAGE). The body is a command,
   * not a table row: its values are read at the edge and passed to the service as primitives.
   *
   * @param body {@code agentCode}, {@code eventType}, {@code summary}, optional {@code detail},
   *     {@code semaphore}
   * @return inserted event
   */
  AgentEventLog tickByCode(Map<String, Object> body);
}
