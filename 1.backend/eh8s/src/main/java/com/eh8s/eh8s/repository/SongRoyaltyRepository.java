package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.ROYALTY_DEPOSIT;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.ROYALTY_SPLIT;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.SYNC_LICENSE_DEAL;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.TRACK;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.DEVNET_PAY_MARKER;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.SyncLicenseDealRecord;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.DevnetPayMarkerRecord;
import com.eh8s.eh8s.repository.interfaces.ISongRoyaltyRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** JOOQ persistence for per-song royalty pools, per-song deposits and sync license deals. */
@Repository
public class SongRoyaltyRepository implements ISongRoyaltyRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public SongRoyaltyRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<ChainConfig> findActiveChainConfig() {
    return dsl.selectFrom(CHAIN_CONFIG)
        .where(CHAIN_CONFIG.IS_ACTIVE.eq((byte) 1))
        .orderBy(CHAIN_CONFIG.ID)
        .limit(1)
        .fetchOptionalInto(ChainConfig.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Track> findTrack(Long trackId) {
    return dsl.selectFrom(TRACK).where(TRACK.ID.eq(trackId)).fetchOptionalInto(Track.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Track> findActivatedTracks() {
    return dsl.selectFrom(TRACK)
        .where(TRACK.POOL_ACTIVATED_AT.isNotNull())
        .orderBy(TRACK.ID)
        .fetchInto(Track.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<RoyaltySplit> findSplits(Long trackId) {
    return dsl.selectFrom(ROYALTY_SPLIT)
        .where(ROYALTY_SPLIT.TRACK_ID.eq(trackId))
        .orderBy(ROYALTY_SPLIT.ID)
        .fetchInto(RoyaltySplit.class);
  }

  /** {@inheritDoc} */
  @Override
  public Track markPoolActivated(
      Long trackId, String royaltyPoolPda, String splitsJson, String txSignature) {
    dsl.update(TRACK)
        .set(TRACK.ROYALTY_POOL_PDA, royaltyPoolPda)
        .set(TRACK.POOL_SPLITS_JSON, splitsJson)
        .set(TRACK.POOL_TX_SIGNATURE, txSignature)
        .set(TRACK.POOL_ACTIVATED_AT, LocalDateTime.now())
        .where(TRACK.ID.eq(trackId))
        .execute();
    return findTrack(trackId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown track"));
  }

  /** {@inheritDoc} */
  @Override
  public Optional<RoyaltyDeposit> findDeposit(Long depositId) {
    return dsl.selectFrom(ROYALTY_DEPOSIT)
        .where(ROYALTY_DEPOSIT.ID.eq(depositId))
        .fetchOptionalInto(RoyaltyDeposit.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<RoyaltyDeposit> findConfirmedDeposits(List<Long> trackIds) {
    if (trackIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(ROYALTY_DEPOSIT)
        .where(ROYALTY_DEPOSIT.TRACK_ID.in(trackIds))
        .and(ROYALTY_DEPOSIT.ON_CHAIN_STATUS.eq("confirmed"))
        .orderBy(ROYALTY_DEPOSIT.ID)
        .fetchInto(RoyaltyDeposit.class);
  }

  /** {@inheritDoc} */
  @Override
  public RoyaltyDeposit markDepositConfirmed(
      Long depositId, String walletPubkey, String txSignature, String royaltyPoolPda) {
    dsl.update(ROYALTY_DEPOSIT)
        .set(ROYALTY_DEPOSIT.DEPOSIT_TX_SIGNATURE, txSignature)
        .set(ROYALTY_DEPOSIT.DEPOSITED_AT, LocalDateTime.now())
        .set(ROYALTY_DEPOSIT.PAYER_WALLET_PUBKEY, walletPubkey)
        .set(ROYALTY_DEPOSIT.ROYALTY_POOL_PDA, royaltyPoolPda)
        .set(ROYALTY_DEPOSIT.ON_CHAIN_STATUS, "confirmed")
        .set(ROYALTY_DEPOSIT.INTENDED_INSTRUCTION, "deposit_royalties")
        .where(ROYALTY_DEPOSIT.ID.eq(depositId))
        .execute();
    RoyaltyDeposit deposit =
        findDeposit(depositId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown deposit"));
    recordMarker(
        walletPubkey,
        deposit.getAmountUsdc(),
        txSignature,
        "deposit_royalties",
        "Confirmed DevNet deposit_royalties depositId=" + depositId + " trackId=" + deposit.getTrackId());
    return deposit;
  }

  /** {@inheritDoc} */
  @Override
  public List<SyncLicenseDeal> findDeals(Long trackId) {
    var query = dsl.selectFrom(SYNC_LICENSE_DEAL);
    if (trackId != null) {
      return query
          .where(SYNC_LICENSE_DEAL.TRACK_ID.eq(trackId))
          .orderBy(SYNC_LICENSE_DEAL.ID.desc())
          .fetchInto(SyncLicenseDeal.class);
    }
    return query.orderBy(SYNC_LICENSE_DEAL.ID.desc()).fetchInto(SyncLicenseDeal.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<SyncLicenseDeal> findPaidDeals(List<Long> trackIds) {
    if (trackIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(SYNC_LICENSE_DEAL)
        .where(SYNC_LICENSE_DEAL.TRACK_ID.in(trackIds))
        .and(SYNC_LICENSE_DEAL.STATUS.eq("paid"))
        .orderBy(SYNC_LICENSE_DEAL.ID)
        .fetchInto(SyncLicenseDeal.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<SyncLicenseDeal> findDeal(Long dealId) {
    return dsl.selectFrom(SYNC_LICENSE_DEAL)
        .where(SYNC_LICENSE_DEAL.ID.eq(dealId))
        .fetchOptionalInto(SyncLicenseDeal.class);
  }

  /** {@inheritDoc} */
  @Override
  public SyncLicenseDeal insertDeal(SyncLicenseDeal deal) {
    SyncLicenseDealRecord rec = dsl.newRecord(SYNC_LICENSE_DEAL, deal);
    rec.changed(SYNC_LICENSE_DEAL.ID, false);
    rec.changed(SYNC_LICENSE_DEAL.CREATED_AT, false);
    rec.store();
    return findDeal(rec.getId()).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public SyncLicenseDeal markDealPaid(
      Long dealId, String walletPubkey, String syncLicensePda, String txSignature) {
    dsl.update(SYNC_LICENSE_DEAL)
        .set(SYNC_LICENSE_DEAL.STATUS, "paid")
        .set(SYNC_LICENSE_DEAL.LICENSEE_WALLET_PUBKEY, walletPubkey)
        .set(SYNC_LICENSE_DEAL.SYNC_LICENSE_PDA, syncLicensePda)
        .set(SYNC_LICENSE_DEAL.PAY_TX_SIGNATURE, txSignature)
        .set(SYNC_LICENSE_DEAL.PAID_AT, LocalDateTime.now())
        .where(SYNC_LICENSE_DEAL.ID.eq(dealId))
        .execute();
    SyncLicenseDeal deal =
        findDeal(dealId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown sync deal"));
    recordMarker(
        walletPubkey,
        deal.getAmountUsdc(),
        txSignature,
        "pay_sync_license",
        "Confirmed DevNet pay_sync_license dealId=" + dealId + " trackId=" + deal.getTrackId());
    return deal;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSignatureRecorded(String txSignature) {
    return dsl.fetchExists(TRACK, TRACK.POOL_TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(ROYALTY_DEPOSIT, ROYALTY_DEPOSIT.DEPOSIT_TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(SYNC_LICENSE_DEAL, SYNC_LICENSE_DEAL.PAY_TX_SIGNATURE.eq(txSignature));
  }

  private void recordMarker(
      String walletPubkey, BigDecimal amount, String txSignature, String instruction, String note) {
    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("pay");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(amount == null ? BigDecimal.ZERO : amount);
    marker.setTxSignature(txSignature);
    marker.setStatus("confirmed");
    marker.setIntendedInstruction(instruction);
    marker.setNote(note);
    marker.setRecordedAt(LocalDateTime.now());
    DevnetPayMarkerRecord rec = dsl.newRecord(DEVNET_PAY_MARKER, marker);
    rec.changed(DEVNET_PAY_MARKER.ID, false);
    rec.store();
  }
}
