package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import java.util.List;

/**
 * Owner dashboard use cases.
 */
public interface IOpsService {

  /**
   * @return twelve agents
   */
  List<OpsAgent> agents();

  /**
   * @return decisions
   */
  List<OwnerDecision> decisions();

  /**
   * Approves a pending yellow semaphore decision from the web inbox so the agent task is executable.
   *
   * @param id decision primary key
   * @return updated decision (answeredVia = web)
   * @throws org.springframework.web.server.ResponseStatusException 409 when already decided
   */
  OwnerDecision approve(Long id);

  /**
   * Rejects a pending decision from the web inbox with an owner note.
   *
   * @param id decision primary key
   * @param rejectReason optional owner note stored as {@code owner_decision.reject_reason}
   *     (blank = "Rejected by owner")
   * @return updated decision (answeredVia = web)
   * @throws org.springframework.web.server.ResponseStatusException 409 when already decided
   */
  OwnerDecision reject(Long id, String rejectReason);

  /**
   * Sets an agent semaphore (green / yellow / red).
   *
   * @param id agent primary key
   * @param semaphore green, yellow or red (required)
   * @return updated agent
   * @throws org.springframework.web.server.ResponseStatusException 400 missing or unknown
   *     semaphore, 404 unknown agent
   */
  OpsAgent setSemaphore(Long id, String semaphore);
}
