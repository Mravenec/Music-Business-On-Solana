package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * Builds authorize_agent / update_musician_level instructions (the backend never signs).
 */
public interface IAgentAuthorityIxBuilder {

  /**
   * Builds {@code authorize_agent(agent_wallet, permissions)}; {@code permissions = 0} revokes.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays the AgentAuthority rent)
   * @param agentWallet agent wallet receiving the bits
   * @param permissions permission bits (subset of {@link #PERM_ALL})
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildAuthorize(
      String programId, String rpcUrl, String ownerWallet, String agentWallet, int permissions);

  /**
   * Builds {@code update_musician_level(new_level)}. When {@code agentSigner} is true the
   * signer's AgentAuthority PDA is passed; otherwise the optional account is the program id
   * (Anchor {@code None}), which only the config owner may use.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param signerWallet owner or pedagogical agent wallet (signer)
   * @param musicianWallet musician whose {@code ["musician", wallet]} profile changes
   * @param newLevel Enigma level 0..5
   * @param agentSigner whether to pass the signer's AgentAuthority PDA
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildLevel(
      String programId,
      String rpcUrl,
      String signerWallet,
      String musicianWallet,
      int newLevel,
      boolean agentSigner);
}
