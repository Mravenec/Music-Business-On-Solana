package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IAgentEventController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import com.eh8s.eh8s.service.interfaces.IAgentEventService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves agent event log JSON and tick mutations.
 */
@RestController
@RequestMapping("/api")
public class AgentEventController implements IAgentEventController {

  private final IAgentEventService agentEventService;

  /**
   * Creates the controller.
   *
   * @param agentEventService tick use cases
   */
  public AgentEventController(IAgentEventService agentEventService) {
    this.agentEventService = agentEventService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/agent-events")
  public List<AgentEventLog> events() {
    return agentEventService.events();
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/agents/{id}/tick")
  public AgentEventLog tick(@PathVariable Long id, @RequestBody AgentEventLog input) {
    return agentEventService.tick(id, input);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/agents/tick")
  public AgentEventLog tickByCode(@RequestBody(required = false) Map<String, Object> body) {
    String code = HttpBody.text(body, "agentCode");
    return agentEventService.record(
        code == null ? "STAGE" : code,
        HttpBody.raw(body, "eventType"),
        HttpBody.raw(body, "semaphore"),
        HttpBody.raw(body, "summary"),
        HttpBody.raw(body, "detail"));
  }
}
