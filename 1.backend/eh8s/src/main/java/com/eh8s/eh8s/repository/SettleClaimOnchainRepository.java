package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONCERT_SETTLEMENT;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.DEVNET_PAY_MARKER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.DevnetPayMarkerRecord;
import com.eh8s.eh8s.repository.interfaces.ISettleClaimOnchainRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/**
 * JOOQ persistence for confirmed settle_concert and claim_royalties DevNet transactions.
 */
@Repository
public class SettleClaimOnchainRepository implements ISettleClaimOnchainRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public SettleClaimOnchainRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<ConcertSettlement> findSettlement(Long settlementId) {
    return dsl.selectFrom(CONCERT_SETTLEMENT)
        .where(CONCERT_SETTLEMENT.ID.eq(settlementId))
        .fetchOptionalInto(ConcertSettlement.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<PendingClaim> findClaim(Long claimId) {
    return dsl.selectFrom(PENDING_CLAIM)
        .where(PENDING_CLAIM.ID.eq(claimId))
        .fetchOptionalInto(PendingClaim.class);
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
  public Optional<String> findFirstClaimWallet(Long settlementId) {
    return dsl.select(ACCOUNT.WALLET_PUBKEY)
        .from(PENDING_CLAIM)
        .join(MUSICIAN_PROFILE)
        .on(MUSICIAN_PROFILE.ID.eq(PENDING_CLAIM.MUSICIAN_PROFILE_ID))
        .join(ACCOUNT)
        .on(ACCOUNT.ID.eq(MUSICIAN_PROFILE.ACCOUNT_ID))
        .where(PENDING_CLAIM.CONCERT_SETTLEMENT_ID.eq(settlementId))
        .orderBy(PENDING_CLAIM.ID)
        .limit(1)
        .fetchOptional(ACCOUNT.WALLET_PUBKEY);
  }

  /** {@inheritDoc} */
  @Override
  public ConcertSettlement markSettlementConfirmed(
      Long settlementId, String walletPubkey, String txSignature, String concertSettlementPda) {
    ConcertSettlement settlement =
        findSettlement(settlementId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown settlement"));
    LocalDateTime now = LocalDateTime.now();
    dsl.update(CONCERT_SETTLEMENT)
        .set(CONCERT_SETTLEMENT.SETTLE_TX_SIGNATURE, txSignature)
        .set(CONCERT_SETTLEMENT.SETTLED_AT, now)
        .set(CONCERT_SETTLEMENT.ON_CHAIN_STATUS, "confirmed")
        .set(CONCERT_SETTLEMENT.STATUS, "settled")
        .where(CONCERT_SETTLEMENT.ID.eq(settlementId))
        .execute();
    dsl.update(CONCERT)
        .set(CONCERT.CONCERT_SETTLEMENT_PDA, concertSettlementPda)
        .where(CONCERT.ID.eq(settlement.getConcertId()))
        .execute();

    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("settle");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(settlement.getGrossUsdc());
    marker.setRelatedClaimId(null);
    marker.setTxSignature(txSignature);
    marker.setStatus("confirmed");
    marker.setIntendedInstruction("settle_concert");
    marker.setNote("Confirmed DevNet settle_concert; pda=" + concertSettlementPda);
    marker.setRecordedAt(now);
    DevnetPayMarkerRecord rec = dsl.newRecord(DEVNET_PAY_MARKER, marker);
    rec.changed(DEVNET_PAY_MARKER.ID, false);
    rec.store();

    return findSettlement(settlementId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update failed"));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecorded(String txSignature) {
    return dsl.fetchExists(
            CONCERT_SETTLEMENT, CONCERT_SETTLEMENT.SETTLE_TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(PENDING_CLAIM, PENDING_CLAIM.TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(DEVNET_PAY_MARKER, DEVNET_PAY_MARKER.TX_SIGNATURE.eq(txSignature));
  }

  /** {@inheritDoc} */
  @Override
  public PendingClaim markClaimConfirmed(
      Long claimId, String walletPubkey, String txSignature, String claimPda) {
    PendingClaim claim =
        findClaim(claimId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown claim"));
    LocalDateTime now = LocalDateTime.now();
    dsl.update(PENDING_CLAIM)
        .set(PENDING_CLAIM.TX_SIGNATURE, txSignature)
        .set(PENDING_CLAIM.CLAIMED_AT, now)
        .set(PENDING_CLAIM.CLAIMER_WALLET_PUBKEY, walletPubkey)
        .set(PENDING_CLAIM.CLAIM_PDA, claimPda)
        .set(PENDING_CLAIM.ON_CHAIN_STATUS, "confirmed")
        .set(PENDING_CLAIM.STATUS, "claimed")
        .where(PENDING_CLAIM.ID.eq(claimId))
        .execute();

    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("claim");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(claim.getAmountUsdc());
    marker.setRelatedClaimId(claimId);
    marker.setTxSignature(txSignature);
    marker.setStatus("confirmed");
    marker.setIntendedInstruction("claim_royalties");
    marker.setNote("Confirmed DevNet claim_royalties USDC transfer");
    marker.setRecordedAt(now);
    DevnetPayMarkerRecord rec = dsl.newRecord(DEVNET_PAY_MARKER, marker);
    rec.changed(DEVNET_PAY_MARKER.ID, false);
    rec.store();

    return findClaim(claimId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update failed"));
  }
}
