package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import java.util.Map;

/**
 * HTTP API for on-chain agent authority (owner) and agent-only Enigma level changes (owner or
 * pedagogical agent).
 */
public interface IAgentAuthorityController {

  /**
   * Agent roster, permission bits, and verified authorizations for the owner wallet.
   *
   * @param walletPubkey caller wallet (config owner)
   * @return roster view
   */
  Map<String, Object> roster(String walletPubkey);

  /**
   * Builds authorize_agent for the owner wallet.
   *
   * @param body {@code walletPubkey}, {@code agentCode}, {@code agentWalletPubkey},
   *     {@code permissions}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildAuthorize(Map<String, Object> body);

  /**
   * Confirms authorize_agent on DevNet and records it.
   *
   * @param body build fields plus {@code txSignature}
   * @return stored authorization
   */
  AgentAuthority confirmAuthorize(Map<String, Object> body);

  /**
   * Musicians the owner or a pedagogical agent may move between Enigma levels.
   *
   * @param walletPubkey caller wallet
   * @return signer role and musician rows
   */
  Map<String, Object> levelTargets(String walletPubkey);

  /**
   * Builds update_musician_level for the owner or a pedagogical agent.
   *
   * @param body {@code walletPubkey}, {@code musicianProfileId}, {@code newLevel}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildLevel(Map<String, Object> body);

  /**
   * Confirms update_musician_level on DevNet, moves the musician, and logs the change.
   *
   * @param body build fields plus {@code txSignature}
   * @return stored level change
   */
  MusicianLevelChange confirmLevel(Map<String, Object> body);
}
