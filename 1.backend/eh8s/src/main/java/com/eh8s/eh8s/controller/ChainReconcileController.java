package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IChainReconcileController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.service.interfaces.IChainReconcileService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner reconcile endpoint under {@code /api/owner/reconcile}.
 */
@RestController
@RequestMapping("/api/owner")
public class ChainReconcileController implements IChainReconcileController {

  private final IChainReconcileService reconcileService;

  /**
   * Creates the controller.
   *
   * @param reconcileService chain reconcile use cases
   */
  public ChainReconcileController(IChainReconcileService reconcileService) {
    this.reconcileService = reconcileService;
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/reconcile")
  public Map<String, Object> reconcile(@RequestBody(required = false) Map<String, Object> body) {
    return reconcileService.reconcile(HttpBody.raw(body, "walletPubkey"));
  }
}
