package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.repository.interfaces.IOpsRepository;
import com.eh8s.eh8s.service.interfaces.IOpsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-app owner dashboard. Web answers record {@code answered_via = web}; a decision already
 * answered (in the web or from Slack) answers 409.
 */
@Service
public class OpsService implements IOpsService {

  private final IOpsRepository opsRepository;

  /**
   * Creates the service.
   *
   * @param opsRepository ops persistence
   */
  public OpsService(IOpsRepository opsRepository) {
    this.opsRepository = opsRepository;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<OpsAgent> agents() {
    return opsRepository.findAgents();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<OwnerDecision> decisions() {
    return opsRepository.findDecisions();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OwnerDecision approve(Long id) {
    return opsRepository.approve(id, "web", null).orElseThrow(OpsService::alreadyDecided);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OwnerDecision reject(Long id, String rejectReason) {
    return opsRepository.reject(id, rejectReason, "web", null).orElseThrow(OpsService::alreadyDecided);
  }

  private static ResponseStatusException alreadyDecided() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "Decision already decided");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OpsAgent setSemaphore(Long id, String semaphore) {
    if (semaphore == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semaphore is required");
    }
    return opsRepository.setSemaphore(id, semaphore);
  }
}
