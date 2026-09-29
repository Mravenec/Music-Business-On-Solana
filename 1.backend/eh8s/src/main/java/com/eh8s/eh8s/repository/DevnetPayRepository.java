package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.DevnetPayMarkerRecord;
import com.eh8s.eh8s.repository.interfaces.IDevnetPayRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.DEVNET_PAY_MARKER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.PENDING_CLAIM;

/**
 * JOOQ persistence for DevNet pay/claim markers.
 */
@Repository
public class DevnetPayRepository implements IDevnetPayRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public DevnetPayRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public List<DevnetPayMarker> findAll() {
    return dsl.selectFrom(DEVNET_PAY_MARKER)
        .orderBy(DEVNET_PAY_MARKER.ID.desc())
        .fetchInto(DevnetPayMarker.class);
  }

  /** {@inheritDoc} */
  @Override
  public DevnetPayMarker insert(DevnetPayMarker marker) {
    DevnetPayMarkerRecord rec = dsl.newRecord(DEVNET_PAY_MARKER, marker);
    rec.changed(DEVNET_PAY_MARKER.ID, false);
    rec.store();
    return rec.into(DevnetPayMarker.class);
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
  public PendingClaim recordClaim(Long claimId, String walletPubkey, String txSignature) {
    PendingClaim claim =
        dsl.selectFrom(PENDING_CLAIM)
            .where(PENDING_CLAIM.ID.eq(claimId))
            .fetchOptionalInto(PendingClaim.class)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown claim"));
    LocalDateTime now = LocalDateTime.now();
    String sig = blankToNull(txSignature);
    dsl.update(PENDING_CLAIM)
        .set(PENDING_CLAIM.STATUS, "claimed")
        .set(PENDING_CLAIM.TX_SIGNATURE, sig)
        .set(PENDING_CLAIM.CLAIMED_AT, now)
        .where(PENDING_CLAIM.ID.eq(claimId))
        .execute();
    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("claim");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(claim.getAmountUsdc());
    marker.setRelatedClaimId(claimId);
    marker.setTxSignature(sig);
    marker.setStatus(sig == null ? "intended" : "submitted");
    marker.setIntendedInstruction("claim_settlement");
    marker.setNote("DevNet claim marker");
    marker.setRecordedAt(now);
    insert(marker);
    return dsl.selectFrom(PENDING_CLAIM)
        .where(PENDING_CLAIM.ID.eq(claimId))
        .fetchOneInto(PendingClaim.class);
  }

  /** {@inheritDoc} */
  @Override
  public AcademySubscription recordPay(Long subscriptionId, String walletPubkey, String txSignature) {
    AcademySubscription sub =
        dsl.selectFrom(ACADEMY_SUBSCRIPTION)
            .where(ACADEMY_SUBSCRIPTION.ID.eq(subscriptionId))
            .fetchOptionalInto(AcademySubscription.class)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown subscription"));
    LocalDateTime now = LocalDateTime.now();
    String sig = blankToNull(txSignature);
    BigDecimal amount =
        Optional.ofNullable(sub.getTreasuryUsdc())
            .orElse(BigDecimal.ZERO)
            .add(Optional.ofNullable(sub.getInstructorUsdc()).orElse(BigDecimal.ZERO));
    dsl.update(ACADEMY_SUBSCRIPTION)
        .set(ACADEMY_SUBSCRIPTION.PAY_TX_SIGNATURE, sig)
        .set(ACADEMY_SUBSCRIPTION.PAID_AT, now)
        .where(ACADEMY_SUBSCRIPTION.ID.eq(subscriptionId))
        .execute();
    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("pay");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(amount);
    marker.setRelatedSubscriptionId(subscriptionId);
    marker.setTxSignature(sig);
    marker.setStatus(sig == null ? "intended" : "submitted");
    marker.setIntendedInstruction("subscribe_academy");
    marker.setNote("DevNet academy pay marker");
    marker.setRecordedAt(now);
    insert(marker);
    return dsl.selectFrom(ACADEMY_SUBSCRIPTION)
        .where(ACADEMY_SUBSCRIPTION.ID.eq(subscriptionId))
        .fetchOneInto(AcademySubscription.class);
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
