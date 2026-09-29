package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.util.List;
import java.util.Optional;

/** Persistence for per-song royalty pools, per-song deposits and sync license deals. */
public interface ISongRoyaltyRepository {

  /**
   * Active chain configuration row.
   *
   * @return config or empty
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Loads a track.
   *
   * @param trackId track id
   * @return track or empty
   */
  Optional<Track> findTrack(Long trackId);

  /**
   * Tracks whose on-chain pool is activated.
   *
   * @return activated tracks ordered by id
   */
  List<Track> findActivatedTracks();

  /**
   * Royalty split rows for a track.
   *
   * @param trackId track id
   * @return splits ordered by id
   */
  List<RoyaltySplit> findSplits(Long trackId);

  /**
   * Stores the confirmed create_royalty_pool snapshot on the track.
   *
   * @param trackId track id
   * @param royaltyPoolPda RoyaltyPool PDA
   * @param splitsJson {@code [{musicianProfileId, wallet, bps}]}
   * @param txSignature confirmed signature
   * @return updated track
   */
  Track markPoolActivated(Long trackId, String royaltyPoolPda, String splitsJson, String txSignature);

  /**
   * Loads a royalty deposit.
   *
   * @param depositId deposit id
   * @return deposit or empty
   */
  Optional<RoyaltyDeposit> findDeposit(Long depositId);

  /**
   * Confirmed deposits for the given tracks.
   *
   * @param trackIds track ids
   * @return confirmed deposits ordered by id
   */
  List<RoyaltyDeposit> findConfirmedDeposits(List<Long> trackIds);

  /**
   * Marks a deposit confirmed after RPC verification.
   *
   * @param depositId deposit id
   * @param walletPubkey depositor wallet
   * @param txSignature confirmed signature
   * @param royaltyPoolPda song RoyaltyPool PDA
   * @return updated deposit
   */
  RoyaltyDeposit markDepositConfirmed(
      Long depositId, String walletPubkey, String txSignature, String royaltyPoolPda);

  /**
   * Sync deals, optionally for one track.
   *
   * @param trackId track id or {@code null} for all
   * @return deals newest first
   */
  List<SyncLicenseDeal> findDeals(Long trackId);

  /**
   * Paid sync deals for the given tracks.
   *
   * @param trackIds track ids
   * @return paid deals ordered by id
   */
  List<SyncLicenseDeal> findPaidDeals(List<Long> trackIds);

  /**
   * Loads a sync deal.
   *
   * @param dealId deal id
   * @return deal or empty
   */
  Optional<SyncLicenseDeal> findDeal(Long dealId);

  /**
   * Inserts a proposed sync deal.
   *
   * @param deal deal row (id ignored)
   * @return stored deal
   */
  SyncLicenseDeal insertDeal(SyncLicenseDeal deal);

  /**
   * Marks a sync deal paid after RPC verification.
   *
   * @param dealId deal id
   * @param walletPubkey licensee wallet
   * @param syncLicensePda SyncLicense PDA
   * @param txSignature confirmed signature
   * @return updated deal
   */
  SyncLicenseDeal markDealPaid(
      Long dealId, String walletPubkey, String syncLicensePda, String txSignature);

  /**
   * Whether a signature is already stored on a track pool, deposit or sync deal.
   *
   * @param txSignature signature
   * @return true when already recorded
   */
  boolean isSignatureRecorded(String txSignature);
}
