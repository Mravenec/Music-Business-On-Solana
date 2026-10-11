package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import java.util.Map;

/**
 * Principal-signed studio power on DevNet (program v0.11.0): {@code grant_studio_role},
 * {@code revoke_studio_role} and {@code set_partner_share}. The backend never signs. Every confirm
 * verifies the signature through {@link ISolanaTxVerifier} before it writes the MariaDB mirrors, and
 * a confirmed mirror wins over the MariaDB-only books path.
 */
public interface IStudioPowerOnchainService {

  /**
   * Console for the principal wallet: program id, confirmed studio admin rows and partner shares.
   *
   * @param walletPubkey caller wallet (config owner)
   * @return console view
   */
  Map<String, Object> console(String walletPubkey);

  /**
   * Builds {@code grant_studio_role(wallet)} for the principal wallet.
   *
   * @param walletPubkey principal wallet (signer)
   * @param adminWalletPubkey wallet receiving {@code studio_admin}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildGrantStudioRole(String walletPubkey, String adminWalletPubkey);

  /**
   * Verifies a {@code grant_studio_role} signature on DevNet, then stores the mirror and syncs the
   * role cache.
   *
   * @param walletPubkey principal wallet (signer)
   * @param adminWalletPubkey wallet receiving {@code studio_admin}
   * @param txSignature base58 signature
   * @return the active mirror row
   */
  StudioRoleGrant confirmGrantStudioRole(
      String walletPubkey, String adminWalletPubkey, String txSignature);

  /**
   * Builds {@code revoke_studio_role(wallet)} for the principal wallet.
   *
   * @param walletPubkey principal wallet (signer)
   * @param adminWalletPubkey wallet losing {@code studio_admin}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildRevokeStudioRole(String walletPubkey, String adminWalletPubkey);

  /**
   * Verifies a {@code revoke_studio_role} signature on DevNet, then marks the mirror revoked and
   * removes the cached studio admin membership.
   *
   * @param walletPubkey principal wallet (signer)
   * @param adminWalletPubkey wallet losing {@code studio_admin}
   * @param txSignature base58 signature
   * @return the revoked mirror row
   */
  StudioRoleGrant confirmRevokeStudioRole(
      String walletPubkey, String adminWalletPubkey, String txSignature);

  /**
   * Builds {@code set_partner_share} for the principal wallet.
   *
   * @param walletPubkey principal wallet (signer)
   * @param partnerWalletPubkey partner whose share changes
   * @param shareBps share of the studio side in basis points; 0 removes the partner
   * @param startsAt window start, unix seconds; {@code null} means 0
   * @param endsAt window end exclusive, unix seconds; {@code null} or 0 means open ended
   * @return wallet-ready instruction
   */
  Map<String, Object> buildSetPartnerShare(
      String walletPubkey,
      String partnerWalletPubkey,
      Integer shareBps,
      Long startsAt,
      Long endsAt);

  /**
   * Verifies a {@code set_partner_share} signature on DevNet, then stores the mirror and repairs
   * the MariaDB partner row from it.
   *
   * @param walletPubkey principal wallet (signer)
   * @param partnerWalletPubkey partner whose share changes
   * @param shareBps share in basis points; 0 removes the partner
   * @param startsAt window start, unix seconds; {@code null} means 0
   * @param endsAt window end exclusive, unix seconds; {@code null} or 0 means open ended
   * @param txSignature base58 signature
   * @return the mirror row (for a removal: the row with its window ended)
   */
  PartnerShare confirmSetPartnerShare(
      String walletPubkey,
      String partnerWalletPubkey,
      Integer shareBps,
      Long startsAt,
      Long endsAt,
      String txSignature);
}
