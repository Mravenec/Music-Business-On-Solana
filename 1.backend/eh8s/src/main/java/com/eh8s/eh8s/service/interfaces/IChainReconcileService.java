package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * On-demand (and optionally scheduled) reconcile: confirmed money rows must match a successful RPC
 * signature. Flags/demotes orphans; never invents USDC. Does not run inside live confirm paths.
 */
public interface IChainReconcileService {

  /**
   * Principal-only reconcile: scans confirmed money rows against DevNet RPC and demotes orphans.
   *
   * @param walletPubkey must match {@code chain_config.owner_wallet_pubkey}
   * @return summary with {@code scanned}, {@code ok}, {@code flagged}, {@code rpcErrors}
   */
  Map<String, Object> reconcile(String walletPubkey);

  /**
   * Scheduled reconcile using the active chain-config owner (no HTTP principal). Same scan rules as
   * {@link #reconcile(String)}.
   *
   * @return summary map, or empty when chain config / RPC URL is missing
   */
  Map<String, Object> reconcileScheduled();
}
