package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for ops agents and owner decisions.
 */
public interface IOpsRepository {

  /**
   * @return the twelve agents
   */
  List<OpsAgent> findAgents();

  /**
   * @param id agent primary key
   * @return the agent, or empty
   */
  Optional<OpsAgent> findAgent(Long id);

  /**
   * @return owner decisions
   */
  List<OwnerDecision> findDecisions();

  /**
   * @param id decision primary key
   * @return the decision, or empty
   */
  Optional<OwnerDecision> findDecision(Long id);

  /**
   * Approves a decision only while it is still pending (one conditional UPDATE, so a second answer
   * from the web or Slack cannot overwrite the first) and marks the agent task executable.
   *
   * @param id decision primary key
   * @param answeredVia web or slack
   * @param answeredBy Slack user or owner wallet (nullable)
   * @return updated row, or empty when the decision was no longer pending
   * @throws org.springframework.web.server.ResponseStatusException 404 for an unknown decision
   */
  Optional<OwnerDecision> approve(Long id, String answeredVia, String answeredBy);

  /**
   * Rejects a decision only while it is still pending and marks the agent task blocked.
   *
   * @param id decision primary key
   * @param rejectNote reason shown to the owner console
   * @param answeredVia web or slack
   * @param answeredBy Slack user or owner wallet (nullable)
   * @return updated row, or empty when the decision was no longer pending
   * @throws org.springframework.web.server.ResponseStatusException 404 for an unknown decision
   */
  Optional<OwnerDecision> reject(Long id, String rejectNote, String answeredVia, String answeredBy);

  /**
   * Sets an agent semaphore color.
   *
   * @param id agent primary key
   * @param semaphore green, yellow, or red
   * @return updated agent
   */
  OpsAgent setSemaphore(Long id, String semaphore);
}
