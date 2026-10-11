package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.PARTNER_SHARE;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.STUDIO_ROLE_GRANT;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import com.eh8s.eh8s.repository.interfaces.IStudioPowerOnchainRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOOQ persistence for the {@code eh8s_onchain.studio_role_grant} and
 * {@code eh8s_onchain.partner_share} mirrors.
 */
@Repository
public class StudioPowerOnchainRepository implements IStudioPowerOnchainRepository {

  private static final String STUDIO_ADMIN = "studio_admin";

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public StudioPowerOnchainRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecorded(String txSignature) {
    return dsl.fetchExists(STUDIO_ROLE_GRANT, STUDIO_ROLE_GRANT.TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(PARTNER_SHARE, PARTNER_SHARE.TX_SIGNATURE.eq(txSignature));
  }

  /** {@inheritDoc} */
  @Override
  public Optional<StudioRoleGrant> findRoleGrant(String walletPubkey) {
    return dsl.selectFrom(STUDIO_ROLE_GRANT)
        .where(STUDIO_ROLE_GRANT.WALLET_PUBKEY.eq(walletPubkey))
        .and(STUDIO_ROLE_GRANT.ROLE.eq(STUDIO_ADMIN))
        .fetchOptionalInto(StudioRoleGrant.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<StudioRoleGrant> findRoleGrants() {
    return dsl.selectFrom(STUDIO_ROLE_GRANT)
        .orderBy(STUDIO_ROLE_GRANT.GRANTED_AT.desc(), STUDIO_ROLE_GRANT.ID.desc())
        .fetchInto(StudioRoleGrant.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public StudioRoleGrant upsertRoleGranted(
      String walletPubkey, String pda, String txSignature, LocalDateTime grantedAt) {
    dsl.insertInto(STUDIO_ROLE_GRANT)
        .set(STUDIO_ROLE_GRANT.WALLET_PUBKEY, walletPubkey)
        .set(STUDIO_ROLE_GRANT.ROLE, STUDIO_ADMIN)
        .set(STUDIO_ROLE_GRANT.GRANTED_AT, grantedAt)
        .setNull(STUDIO_ROLE_GRANT.REVOKED_AT)
        .set(STUDIO_ROLE_GRANT.TX_SIGNATURE, txSignature)
        .set(STUDIO_ROLE_GRANT.PDA, pda)
        .onDuplicateKeyUpdate()
        .set(STUDIO_ROLE_GRANT.WALLET_PUBKEY, walletPubkey)
        .set(STUDIO_ROLE_GRANT.GRANTED_AT, grantedAt)
        .setNull(STUDIO_ROLE_GRANT.REVOKED_AT)
        .set(STUDIO_ROLE_GRANT.TX_SIGNATURE, txSignature)
        .execute();
    return findRoleGrant(walletPubkey).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public StudioRoleGrant upsertRoleRevoked(
      String walletPubkey, String pda, String txSignature, LocalDateTime revokedAt) {
    dsl.insertInto(STUDIO_ROLE_GRANT)
        .set(STUDIO_ROLE_GRANT.WALLET_PUBKEY, walletPubkey)
        .set(STUDIO_ROLE_GRANT.ROLE, STUDIO_ADMIN)
        .set(STUDIO_ROLE_GRANT.GRANTED_AT, revokedAt)
        .set(STUDIO_ROLE_GRANT.REVOKED_AT, revokedAt)
        .set(STUDIO_ROLE_GRANT.TX_SIGNATURE, txSignature)
        .set(STUDIO_ROLE_GRANT.PDA, pda)
        .onDuplicateKeyUpdate()
        .set(STUDIO_ROLE_GRANT.REVOKED_AT, revokedAt)
        .set(STUDIO_ROLE_GRANT.TX_SIGNATURE, txSignature)
        .execute();
    return findRoleGrant(walletPubkey).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  public Optional<PartnerShare> findPartnerShare(String walletPubkey) {
    return dsl.selectFrom(PARTNER_SHARE)
        .where(PARTNER_SHARE.WALLET_PUBKEY.eq(walletPubkey))
        .fetchOptionalInto(PartnerShare.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<PartnerShare> findPartnerShares() {
    return dsl.selectFrom(PARTNER_SHARE)
        .orderBy(PARTNER_SHARE.WALLET_PUBKEY.asc())
        .fetchInto(PartnerShare.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public PartnerShare upsertPartnerShare(
      String walletPubkey,
      int shareBps,
      LocalDateTime startedAt,
      LocalDateTime endedAt,
      String pda,
      String txSignature) {
    dsl.insertInto(PARTNER_SHARE)
        .set(PARTNER_SHARE.WALLET_PUBKEY, walletPubkey)
        .set(PARTNER_SHARE.SHARE_BPS, shareBps)
        .set(PARTNER_SHARE.STARTED_AT, startedAt)
        .set(PARTNER_SHARE.ENDED_AT, endedAt)
        .set(PARTNER_SHARE.TX_SIGNATURE, txSignature)
        .set(PARTNER_SHARE.PDA, pda)
        .onDuplicateKeyUpdate()
        .set(PARTNER_SHARE.SHARE_BPS, shareBps)
        .set(PARTNER_SHARE.STARTED_AT, startedAt)
        .set(PARTNER_SHARE.ENDED_AT, endedAt)
        .set(PARTNER_SHARE.TX_SIGNATURE, txSignature)
        .execute();
    return findPartnerShare(walletPubkey).orElseThrow();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public Optional<PartnerShare> endPartnerShare(
      String walletPubkey, LocalDateTime endedAt, String txSignature) {
    int updated =
        dsl.update(PARTNER_SHARE)
            .set(PARTNER_SHARE.ENDED_AT, endedAt)
            .set(PARTNER_SHARE.TX_SIGNATURE, txSignature)
            .where(PARTNER_SHARE.WALLET_PUBKEY.eq(walletPubkey))
            .execute();
    return updated == 0 ? Optional.empty() : findPartnerShare(walletPubkey);
  }
}
