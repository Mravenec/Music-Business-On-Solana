package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademyPlan;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.DevnetPayMarkerRecord;
import com.eh8s.eh8s.repository.interfaces.IAcademyOnchainRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_PLAN;
import static com.eh8s.eh8s.database.jooq.eh8s_academy.Tables.ACADEMY_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.DEVNET_PAY_MARKER;

/**
 * JOOQ persistence for confirmed academy subscribe_academy DevNet payments.
 */
@Repository
public class AcademyOnchainRepository implements IAcademyOnchainRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public AcademyOnchainRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<AcademySubscription> findSubscription(Long subscriptionId) {
    return dsl.selectFrom(ACADEMY_SUBSCRIPTION)
        .where(ACADEMY_SUBSCRIPTION.ID.eq(subscriptionId))
        .fetchOptionalInto(AcademySubscription.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<AcademyPlan> findPlan(Long planId) {
    return dsl.selectFrom(ACADEMY_PLAN)
        .where(ACADEMY_PLAN.ID.eq(planId))
        .fetchOptionalInto(AcademyPlan.class);
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
  public AcademySubscription markConfirmed(
      Long subscriptionId,
      String walletPubkey,
      String txSignature,
      String academySubscriptionPda,
      LocalDateTime onchainExpiresAt) {
    AcademySubscription sub =
        findSubscription(subscriptionId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown subscription"));
    LocalDateTime now = LocalDateTime.now();
    dsl.update(ACADEMY_SUBSCRIPTION)
        .set(ACADEMY_SUBSCRIPTION.PAY_TX_SIGNATURE, txSignature)
        .set(ACADEMY_SUBSCRIPTION.PAID_AT, now)
        .set(ACADEMY_SUBSCRIPTION.PAYER_WALLET_PUBKEY, walletPubkey)
        .set(ACADEMY_SUBSCRIPTION.ACADEMY_SUBSCRIPTION_PDA, academySubscriptionPda)
        .set(ACADEMY_SUBSCRIPTION.ON_CHAIN_STATUS, "confirmed")
        .set(ACADEMY_SUBSCRIPTION.ONCHAIN_EXPIRES_AT, onchainExpiresAt)
        .set(ACADEMY_SUBSCRIPTION.INTENDED_INSTRUCTION, "subscribe_academy")
        .where(ACADEMY_SUBSCRIPTION.ID.eq(subscriptionId))
        .execute();

    BigDecimal amount =
        Optional.ofNullable(sub.getTreasuryUsdc())
            .orElse(BigDecimal.ZERO)
            .add(Optional.ofNullable(sub.getInstructorUsdc()).orElse(BigDecimal.ZERO));
    DevnetPayMarker marker = new DevnetPayMarker();
    marker.setKind("pay");
    marker.setWalletPubkey(walletPubkey);
    marker.setAmountUsdc(amount);
    marker.setRelatedSubscriptionId(subscriptionId);
    marker.setTxSignature(txSignature);
    marker.setStatus("confirmed");
    marker.setIntendedInstruction("subscribe_academy");
    marker.setNote("Confirmed DevNet subscribe_academy");
    marker.setRecordedAt(now);
    DevnetPayMarkerRecord rec = dsl.newRecord(DEVNET_PAY_MARKER, marker);
    rec.changed(DEVNET_PAY_MARKER.ID, false);
    rec.store();

    return findSubscription(subscriptionId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update failed"));
  }
}
