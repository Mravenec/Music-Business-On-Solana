package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IChainReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional nightly reconcile. Enabled only with {@code eh8s.reconcile.scheduled=true}. On-demand
 * {@code POST /api/owner/reconcile} stays available regardless.
 */
@Component
@ConditionalOnProperty(name = "eh8s.reconcile.scheduled", havingValue = "true")
public class ChainReconcileScheduler {

  private static final Logger log = LoggerFactory.getLogger(ChainReconcileScheduler.class);

  private final IChainReconcileService reconcileService;

  /**
   * Creates the scheduler.
   *
   * @param reconcileService chain reconcile use cases
   */
  public ChainReconcileScheduler(IChainReconcileService reconcileService) {
    this.reconcileService = reconcileService;
  }

  /**
   * Runs the same scan as the owner POST (no invented USDC; demote orphans only).
   */
  @Scheduled(cron = "${eh8s.reconcile.cron:0 0 3 * * *}")
  public void nightly() {
    var summary = reconcileService.reconcileScheduled();
    log.info(
        "Chain reconcile finished: scanned={} ok={} flagged={} rpcErrors={}",
        summary.get("scanned"),
        summary.get("ok"),
        summary.get("flagged") instanceof java.util.List<?> f ? f.size() : 0,
        summary.get("rpcErrors") instanceof java.util.List<?> e ? e.size() : 0);
  }
}
