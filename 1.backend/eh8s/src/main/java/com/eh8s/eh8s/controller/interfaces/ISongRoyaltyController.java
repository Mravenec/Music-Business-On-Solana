package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import java.util.List;
import java.util.Map;

/** HTTP contract for per-song royalty pools, sync license deals and per-song credits. */
public interface ISongRoyaltyController {

  /**
   * Song pool status (proposed or activated splits, on-chain totals).
   *
   * @param trackId track id
   * @return status map
   */
  Map<String, Object> poolStatus(Long trackId);

  /**
   * Builds create_royalty_pool for wallet signing.
   *
   * @param trackId track id
   * @param body walletPubkey and optional splits
   * @return instruction payload
   */
  Map<String, Object> buildPool(Long trackId, Map<String, Object> body);

  /**
   * Confirms a create_royalty_pool signature and activates the song pool.
   *
   * @param trackId track id
   * @param body walletPubkey, txSignature and the same optional splits
   * @return activated track
   */
  Track confirmPool(Long trackId, Map<String, Object> body);

  /**
   * Lists sync license deals.
   *
   * @param trackId optional track filter
   * @return deals newest first
   */
  List<SyncLicenseDeal> deals(Long trackId);

  /**
   * Proposes a sync license deal (80% artist / 20% EH8S).
   *
   * @param deal trackId, licenseeName, useDescription, amountUsdc
   * @return stored deal
   */
  SyncLicenseDeal createDeal(SyncLicenseDeal deal);

  /**
   * Builds pay_sync_license for wallet signing.
   *
   * @param dealId deal id
   * @param body walletPubkey
   * @return instruction payload
   */
  Map<String, Object> buildPay(Long dealId, Map<String, Object> body);

  /**
   * Confirms a pay_sync_license signature and marks the deal paid.
   *
   * @param dealId deal id
   * @param body walletPubkey and txSignature
   * @return paid deal
   */
  SyncLicenseDeal confirmPay(Long dealId, Map<String, Object> body);

  /**
   * Per-song credits for a musician wallet.
   *
   * @param walletPubkey musician wallet
   * @return credit rows newest first
   */
  List<Map<String, Object>> songCredits(String walletPubkey);
}
