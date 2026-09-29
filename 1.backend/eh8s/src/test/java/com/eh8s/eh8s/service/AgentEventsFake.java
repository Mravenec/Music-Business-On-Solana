package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import com.eh8s.eh8s.service.interfaces.IAgentEventService;
import java.util.ArrayList;
import java.util.List;

/** In-memory agent event log for agent service tests. */
class AgentEventsFake implements IAgentEventService {

  final List<AgentEventLog> recorded = new ArrayList<>();
  final List<String> codes = new ArrayList<>();

  @Override
  public List<AgentEventLog> events() {
    return recorded;
  }

  @Override
  public AgentEventLog tick(Long agentId, AgentEventLog input) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AgentEventLog record(
      String agentCode, String eventType, String semaphore, String summary, String detail) {
    AgentEventLog e = new AgentEventLog();
    e.setEventType(eventType);
    e.setSemaphore(semaphore);
    e.setSummary(summary);
    e.setDetail(detail);
    codes.add(agentCode);
    recorded.add(e);
    return e;
  }
}
