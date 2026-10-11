package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the MariaDB mirrors of the program v0.11.0 {@code StudioRole} and
 * {@code PartnerShare} PDAs. A row is written only after the signature was verified on DevNet.
 */
public interface IStudioPowerOnchainRepository {

  /**
   * Whether a signature already produced a studio role or partner share mirror row.
   *
   * @param txSignature base58 transaction signature
   * @return true when it is already stored
   */
  boolean isRecorded(String txSignature);

  /**
   * Loads the studio role mirror of one wallet (active or revoked).
   *
   * @param walletPubkey wallet that holds or held {@code studio_admin}
   * @return the mirror row, or empty when no confirmed grant or revoke exists
   */
  Optional<StudioRoleGrant> findRoleGrant(String walletPubkey);

  /**
   * Every studio role mirror row, newest grant first.
   *
   * @return role mirror rows
   */
  List<StudioRoleGrant> findRoleGrants();

  /**
   * Stores a confirmed {@code grant_studio_role}: one row per StudioRole PDA, active afterwards.
   *
   * @param walletPubkey wallet receiving {@code studio_admin}
   * @param pda StudioRole PDA
   * @param txSignature verified signature
   * @param grantedAt confirm time
   * @return the stored row
   */
  StudioRoleGrant upsertRoleGranted(
      String walletPubkey, String pda, String txSignature, LocalDateTime grantedAt);

  /**
   * Stores a confirmed {@code revoke_studio_role}: the row stays and is marked revoked.
   *
   * @param walletPubkey wallet losing {@code studio_admin}
   * @param pda StudioRole PDA
   * @param txSignature verified signature
   * @param revokedAt confirm time
   * @return the stored row
   */
  StudioRoleGrant upsertRoleRevoked(
      String walletPubkey, String pda, String txSignature, LocalDateTime revokedAt);

  /**
   * Loads the partner share mirror of one wallet (live, scheduled or ended).
   *
   * @param walletPubkey partner wallet
   * @return the mirror row, or empty when the chain never confirmed a share for it
   */
  Optional<PartnerShare> findPartnerShare(String walletPubkey);

  /**
   * Every partner share mirror row, by wallet.
   *
   * @return share mirror rows
   */
  List<PartnerShare> findPartnerShares();

  /**
   * Stores a confirmed {@code set_partner_share} with a positive share: one row per PartnerShare
   * PDA, replaced by the latest confirmed window.
   *
   * @param walletPubkey partner wallet
   * @param shareBps share of the studio side in basis points (1..10000)
   * @param startedAt window start
   * @param endedAt window end (exclusive), or {@code null} when open ended
   * @param pda PartnerShare PDA
   * @param txSignature verified signature
   * @return the stored row
   */
  PartnerShare upsertPartnerShare(
      String walletPubkey,
      int shareBps,
      LocalDateTime startedAt,
      LocalDateTime endedAt,
      String pda,
      String txSignature);

  /**
   * Stores a confirmed {@code set_partner_share} with 0 bps: the row keeps its last share and its
   * window ends at {@code endedAt}. When the wallet has no row yet nothing is stored.
   *
   * @param walletPubkey partner wallet
   * @param endedAt confirm time
   * @param txSignature verified signature
   * @return the updated row, or empty when the wallet never had a share mirror
   */
  Optional<PartnerShare> endPartnerShare(
      String walletPubkey, LocalDateTime endedAt, String txSignature);
}
