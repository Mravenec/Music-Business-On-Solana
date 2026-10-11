package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * Builds the program v0.11.0 studio power instructions: {@code grant_studio_role},
 * {@code revoke_studio_role} and {@code set_partner_share}. The backend never signs; the
 * principal (config owner) wallet signs what these payloads describe.
 */
public interface IStudioPowerIxBuilder {

  /**
   * Builds {@code grant_studio_role(wallet)}: the {@code studio_admin} role in the
   * {@code ["studio_role", wallet]} PDA. Refused on chain once governance exists.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays the StudioRole rent)
   * @param adminWallet wallet receiving studio_admin
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildGrantStudioRole(
      String programId, String rpcUrl, String ownerWallet, String adminWallet);

  /**
   * Builds {@code revoke_studio_role(wallet)}: the PDA stays with role 0. Fails on chain when
   * the wallet never held the role.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param adminWallet wallet losing studio_admin
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildRevokeStudioRole(
      String programId, String rpcUrl, String ownerWallet, String adminWallet);

  /**
   * Builds {@code set_partner_share(wallet, share_bps, starts_at, ends_at)}. {@code shareBps = 0}
   * removes the partner; the window is {@code [startsAt, endsAt)} in unix seconds with
   * {@code endsAt = 0} meaning open ended. Overlapping shares must never sum above 10000 bps.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays the PartnerShare / PartnerBook rent)
   * @param partnerWallet partner whose studio share changes
   * @param shareBps share of the studio side in basis points (0..10000)
   * @param startsAt window start, unix seconds (0 or later)
   * @param endsAt window end exclusive, unix seconds; 0 for open ended
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildSetPartnerShare(
      String programId,
      String rpcUrl,
      String ownerWallet,
      String partnerWallet,
      int shareBps,
      long startsAt,
      long endsAt);
}