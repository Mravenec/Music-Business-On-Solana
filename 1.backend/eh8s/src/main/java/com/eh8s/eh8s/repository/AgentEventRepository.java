package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.records.AgentEventLogRecord;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.records.OwnerDecisionRecord;
import com.eh8s.eh8s.repository.interfaces.IAgentEventRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.AGENT_EVENT_LOG;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.OPS_AGENT;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.OWNER_DECISION;

/**
 * JOOQ persistence for agent event ticks.
 */
@Repository
public class AgentEventRepository implements IAgentEventRepository {

  private final DSLContext dsl;

  /** @param dsl JOOQ context */
  public AgentEventRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<AgentEventLog> findAll() {
    return dsl.selectFrom(AGENT_EVENT_LOG)
        .orderBy(AGENT_EVENT_LOG.ID.desc())
        .fetchInto(AgentEventLog.class);
  }

  @Override
  public Optional<OpsAgent> findAgent(Long id) {
    return dsl.selectFrom(OPS_AGENT).where(OPS_AGENT.ID.eq(id)).fetchOptionalInto(OpsAgent.class);
  }

  @Override
  public Optional<OpsAgent> findAgentByCode(String code) {
    return dsl.selectFrom(OPS_AGENT).where(OPS_AGENT.CODE.eq(code)).fetchOptionalInto(OpsAgent.class);
  }

  @Override
  public OwnerDecision insertDecision(OwnerDecision decision) {
    OwnerDecisionRecord rec = dsl.newRecord(OWNER_DECISION, decision);
    rec.changed(OWNER_DECISION.ID, false);
    rec.store();
    return rec.into(OwnerDecision.class);
  }

  @Override
  public AgentEventLog insertEvent(AgentEventLog event) {
    AgentEventLogRecord rec = dsl.newRecord(AGENT_EVENT_LOG, event);
    rec.changed(AGENT_EVENT_LOG.ID, false);
    rec.store();
    return rec.into(AgentEventLog.class);
  }
}
