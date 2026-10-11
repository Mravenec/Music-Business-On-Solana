package com.eh8s.eh8s.controller.interfaces;

import java.util.Map;

/**
 * Principal-only HTTP API for on-demand chain reconcile under {@code /api/owner/reconcile}.
 */
public interface IChainReconcileController {

  /**
   * Scans confirmed money rows vs RPC and demotes orphans.
   *
   * @param body command body {@code {walletPubkey}}; only that string reaches the service
   * @return reconcile summary
   */
  Map<String, Object> reconcile(Map<String, Object> body);
}
