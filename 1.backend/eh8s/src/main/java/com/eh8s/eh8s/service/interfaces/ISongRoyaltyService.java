package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import java.util.List;
import java.util.Map;

/**
 * Per-song royalty pools (create_royalty_pool), per-song deposits (deposit_royalties) and sync
 * licensing 80/20 (pay_sync_license). Never marks anything paid without a verified DevNet
 * signature.
 */
public interface ISongRoyaltyService {

  /**
   * Song pool status: proposed splits, activated snapshot and on-chain totals.
   *
   * @param trackId track id
   * @return status map
   */
  Map<String, Object> poolStatus(Long trackId);

  /**
   * Builds create_royalty_pool for the song. Splits come from {@code body.splits}
   * ({@code [{musicianProfileId, bps}]}), else the track royalty_split rows, else the band members
   * split equally.
   *
   * @param trackId track id
   * @param walletPubkey signer wallet (base58)
   * @param splits explicit splits (musicianProfileId + shareBps summing to 10000), or null for stored/band splits
   * @return instruction payload
   */
  Map<String, Object> buildActivatePool(Long trackId,String walletPubkey, List<RoyaltySplit> splits);

  /**
   * Verifies a create_royalty_pool signature and stores the split snapshot on the track.
   *
   * @param trackId track id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @param splits explicit splits (musicianProfileId + shareBps summing to 10000), or null for stored/band splits
   * @return activated track
   */
  Track confirmActivatePool(Long trackId,String walletPubkey, String txSignature, List<RoyaltySplit> splits);

  /**
   * Builds deposit_royalties for a royalty_deposit row into its song pool.
   *
   * @param depositId royalty_deposit id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildDeposit(Long depositId,String walletPubkey);

  /**
   * Verifies a deposit_royalties signature and marks the deposit confirmed.
   *
   * @param depositId royalty_deposit id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return confirmed deposit
   */
  RoyaltyDeposit confirmDeposit(Long depositId,String walletPubkey, String txSignature);

  /**
   * Sync license deals, optionally for one track.
   *
   * @param trackId track id or {@code null}
   * @return deals newest first
   */
  List<SyncLicenseDeal> deals(Long trackId);

  /**
   * Proposes a sync license deal; computes the 80% artist / 20% EH8S split.
   *
   * @param deal trackId, licenseeName, useDescription, amountUsdc
   * @return stored deal
   */
  SyncLicenseDeal createDeal(SyncLicenseDeal deal);

  /**
   * Builds pay_sync_license for a proposed deal (licensee signs).
   *
   * @param dealId sync_license_deal id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildPaySync(Long dealId,String walletPubkey);

  /**
   * Verifies a pay_sync_license signature and marks the deal paid.
   *
   * @param dealId sync_license_deal id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return paid deal
   */
  SyncLicenseDeal confirmPaySync(Long dealId,String walletPubkey, String txSignature);

  /**
   * Per-song credits for a musician wallet: its split of every confirmed deposit and paid sync
   * deal on activated songs.
   *
   * @param walletPubkey musician wallet
   * @return credit rows newest first
   */
  List<Map<String, Object>> songCredits(String walletPubkey);
}
