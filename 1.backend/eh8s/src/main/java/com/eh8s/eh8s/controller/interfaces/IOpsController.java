package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import java.util.List;

/**
 * HTTP contract for the owner dashboard.
 */
public interface IOpsController {

  /**
   * @return agents
   */
  List<OpsAgent> agents();

  /**
   * @return decisions
   */
  List<OwnerDecision> decisions();

  /**
   * @param id decision to approve
   * @return updated decision
   */
  OwnerDecision approve(Long id);

  /**
   * @param id decision to reject
   * @param input decision POJO carrying the optional {@code rejectReason}
   * @return updated decision
   */
  OwnerDecision reject(Long id, OwnerDecision input);

  /**
   * @param id agent to update
   * @param input agent POJO carrying {@code semaphore}
   * @return updated agent
   */
  OpsAgent setSemaphore(Long id, OpsAgent input);
}
