package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IOpsController;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.service.interfaces.IOpsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves owner dashboard JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class OpsController implements IOpsController {

  private final IOpsService opsService;

  /**
   * Creates the controller.
   *
   * @param opsService ops use cases
   */
  public OpsController(IOpsService opsService) {
    this.opsService = opsService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/agents")
  public List<OpsAgent> agents() {
    return opsService.agents();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/owner-decisions")
  public List<OwnerDecision> decisions() {
    return opsService.decisions();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/owner-decisions/{id}/approve")
  public OwnerDecision approve(@PathVariable Long id) {
    return opsService.approve(id);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/owner-decisions/{id}/reject")
  public OwnerDecision reject(@PathVariable Long id, @RequestBody(required = false) OwnerDecision input) {
    return opsService.reject(id, input == null ? null : input.getRejectReason());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/agents/{id}/semaphore")
  public OpsAgent setSemaphore(@PathVariable Long id, @RequestBody OpsAgent input) {
    return opsService.setSemaphore(id, input.getSemaphore());
  }
}
