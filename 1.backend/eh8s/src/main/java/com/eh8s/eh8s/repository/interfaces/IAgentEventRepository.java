package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentEventLog;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for agent event ticks and linked owner decisions.
 */
public interface IAgentEventRepository {

  /** @return events newest first */
  List<AgentEventLog> findAll();

  /** @param id agent id */
  Optional<OpsAgent> findAgent(Long id);

  /** @param code agent code */
  Optional<OpsAgent> findAgentByCode(String code);

  /**
   * Inserts an owner decision for yellow/red ticks.
   *
   * @param decision unsaved decision
   * @return stored decision
   */
  OwnerDecision insertDecision(OwnerDecision decision);

  /**
   * Inserts an agent event log row.
   *
   * @param event unsaved event
   * @return stored event
   */
  AgentEventLog insertEvent(AgentEventLog event);
}
