package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IAgentEventRepository;
import com.eh8s.eh8s.service.interfaces.ISlackBridgeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AgentEventServiceTest {

  private final List<AgentEventLog> events = new ArrayList<>();
  private final List<OwnerDecision> decisions = new ArrayList<>();
  private final List<Long> slackDecisionIds = new ArrayList<>();

  private final OpsAgent stage = agent(4L, "STAGE", "green");

  private final IAgentEventRepository repo =
      new IAgentEventRepository() {
        @Override
        public List<AgentEventLog> findAll() {
          return events;
        }

        @Override
        public Optional<OpsAgent> findAgent(Long id) {
          return stage.getId().equals(id) ? Optional.of(stage) : Optional.empty();
        }

        @Override
        public Optional<OpsAgent> findAgentByCode(String code) {
          return stage.getCode().equals(code) ? Optional.of(stage) : Optional.empty();
        }

        @Override
        public OwnerDecision insertDecision(OwnerDecision decision) {
          decision.setId(100L + decisions.size());
          decisions.add(decision);
          return decision;
        }

        @Override
        public AgentEventLog insertEvent(AgentEventLog event) {
          event.setId(1L + events.size());
          events.add(event);
          return event;
        }
      };

  private final ISlackBridgeService slack =
      new ISlackBridgeService() {
        @Override
        public Map<String, Object> status(String walletPubkey) {
          return Map.of();
        }

        @Override
        public List<SlackDeliveryLog> deliveries(String walletPubkey) {
          return List.of();
        }

        @Override
        public SlackDeliveryLog notify(
            String walletPubkey, String text, Long ownerDecisionId, Long agentEventId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public SlackDeliveryLog notifyDecision(OwnerDecision decision) {
          slackDecisionIds.add(decision.getId());
          return null;
        }
      };

  private final AgentEventService service = new AgentEventService(repo, slack);

  private static OpsAgent agent(Long id, String code, String semaphore) {
    OpsAgent a = new OpsAgent();
    a.setId(id);
    a.setCode(code);
    a.setSemaphore(semaphore);
    return a;
  }

  private static AgentEventLog tick(String type, String semaphore, String summary, String detail) {
    AgentEventLog in = new AgentEventLog();
    in.setEventType(type);
    in.setSemaphore(semaphore);
    in.setSummary(summary);
    in.setDetail(detail);
    return in;
  }

  @Test
  void greenTickPojoStoresEventWithoutDecision() {
    AgentEventLog row = service.tick(4L, tick(" routine ", "GREEN", " all fine ", null));
    assertEquals(4L, row.getOpsAgentId());
    assertEquals("routine", row.getEventType());
    assertEquals("green", row.getSemaphore());
    assertEquals("all fine", row.getSummary());
    assertEquals("all fine", row.getDetail(), "detail defaults to the summary");
    assertNull(row.getOwnerDecisionId());
    assertEquals(0, decisions.size());
  }

  @Test
  void yellowRecordByCodeOpensDecisionAndNotifiesSlack() {
    AgentEventLog row = service.record("STAGE", "venue_wait", "yellow", "approve venue", "below 1500");
    assertEquals(1, decisions.size());
    assertEquals("pending", decisions.get(0).getStatus());
    assertEquals(decisions.get(0).getId(), row.getOwnerDecisionId());
    assertEquals(List.of(decisions.get(0).getId()), slackDecisionIds);
  }

  @Test
  void nullSemaphoreUsesAgentDefaultAndBadInputIsRejected() {
    assertEquals("green", service.tick(4L, tick("routine", null, "ok", null)).getSemaphore());
    assertEquals(400, status(() -> service.tick(4L, null)));
    assertEquals(400, status(() -> service.tick(4L, tick("routine", "blue", "ok", null))));
    assertEquals(400, status(() -> service.tick(4L, tick(" ", "green", "ok", null))));
    assertEquals(404, status(() -> service.tick(9L, tick("routine", "green", "ok", null))));
    assertEquals(404, status(() -> service.record("NOPE", "routine", "green", "ok", null)));
  }

  private static int status(Runnable r) {
    return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
  }
}
