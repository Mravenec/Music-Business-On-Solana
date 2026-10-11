package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import java.util.Map;

/**
 * HTTP API for principal-signed studio power on DevNet: build + confirm for
 * {@code grant_studio_role}, {@code revoke_studio_role} and {@code set_partner_share}.
 */
public interface IStudioPowerOnchainController {

  /**
   * Confirmed studio admin and partner share mirrors for the principal wallet.
   *
   * @param walletPubkey caller wallet (config owner)
   * @return console view
   */
  Map<String, Object> console(String walletPubkey);

  /**
   * Builds {@code grant_studio_role}.
   *
   * @param body {@code walletPubkey} (principal), {@code adminWalletPubkey}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildGrant(Map<String, Object> body);

  /**
   * Confirms {@code grant_studio_role} on DevNet and records the mirror.
   *
   * @param body build fields plus {@code txSignature}
   * @return active studio role mirror
   */
  StudioRoleGrant confirmGrant(Map<String, Object> body);

  /**
   * Builds {@code revoke_studio_role}.
   *
   * @param body {@code walletPubkey} (principal), {@code adminWalletPubkey}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildRevoke(Map<String, Object> body);

  /**
   * Confirms {@code revoke_studio_role} on DevNet and records the mirror.
   *
   * @param body build fields plus {@code txSignature}
   * @return revoked studio role mirror
   */
  StudioRoleGrant confirmRevoke(Map<String, Object> body);

  /**
   * Builds {@code set_partner_share}.
   *
   * @param body {@code walletPubkey} (principal), {@code partnerWalletPubkey}, {@code shareBps},
   *     optional {@code startsAt} / {@code endsAt} (unix seconds)
   * @return wallet-ready instruction
   */
  Map<String, Object> buildPartnerShare(Map<String, Object> body);

  /**
   * Confirms {@code set_partner_share} on DevNet and records the mirror.
   *
   * @param body build fields plus {@code txSignature}
   * @return partner share mirror
   */
  PartnerShare confirmPartnerShare(Map<String, Object> body);
}
