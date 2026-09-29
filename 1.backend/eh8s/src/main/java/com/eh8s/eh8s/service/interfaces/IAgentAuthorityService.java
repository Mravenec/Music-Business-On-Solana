package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import java.util.Map;

/**
 * On-chain agent authority: the owner grants permission bits to agent wallets
 * (authorize_agent) and only the owner or a pedagogical (NEXUS, 0x01) agent changes a
 * musician's Enigma level (update_musician_level). Every write is recorded only after the
 * DevNet transaction is verified.
 */
public interface IAgentAuthorityService {

  /**
   * Owner view: agent roster, permission bit legend, and verified authorizations.
   *
   * @param walletPubkey caller wallet (must be the config owner)
   * @return roster payload
   * @throws org.springframework.web.server.ResponseStatusException 400 without wallet, 403 for a
   *     non-owner
   */
  Map<String, Object> roster(String walletPubkey);

  /**
   * Builds authorize_agent for the owner to sign.
   *
   * @param walletPubkey signer wallet (base58)
   * @param agentCode ops agent code (for example NEXUS)
   * @param agentWalletPubkey agent signer wallet (base58)
   * @param permissions permission bitmask (bits 0x01..0x10)
   * @return instruction payload
   * @throws org.springframework.web.server.ResponseStatusException 400 bad bits / wallet /
   *     agent, 403 non-owner
   */
  Map<String, Object> buildAuthorize(String walletPubkey, String agentCode, String agentWalletPubkey, Integer permissions);

  /**
   * Verifies a signed authorize_agent on DevNet, then records it.
   *
   * @param walletPubkey signer wallet (base58)
   * @param agentCode ops agent code (for example NEXUS)
   * @param agentWalletPubkey agent signer wallet (base58)
   * @param permissions permission bitmask (bits 0x01..0x10)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return stored authorization
   * @throws org.springframework.web.server.ResponseStatusException 400 failed verification, 409
   *     signature already recorded
   */
  AgentAuthority confirmAuthorize(String walletPubkey, String agentCode, String agentWalletPubkey, Integer permissions, String txSignature);

  /**
   * Level console for the owner or a pedagogical agent: musicians with a wallet and their level.
   *
   * @param walletPubkey caller wallet
   * @return signer role plus musician rows
   * @throws org.springframework.web.server.ResponseStatusException 403 when the wallet holds no
   *     live 0x01 authorization and is not the owner
   */
  Map<String, Object> levelTargets(String walletPubkey);

  /**
   * Builds update_musician_level for the owner or a pedagogical agent to sign.
   *
   * @param walletPubkey signer wallet (base58)
   * @param musicianProfileId target musician profile id
   * @param newLevel new Enigma level 0..5
   * @return instruction payload
   * @throws org.springframework.web.server.ResponseStatusException 400 bad level, 403 no power,
   *     404 unknown musician
   */
  Map<String, Object> buildLevel(String walletPubkey, Long musicianProfileId, Integer newLevel);

  /**
   * Verifies a signed update_musician_level on DevNet, then moves the musician and logs it.
   *
   * @param walletPubkey signer wallet (base58)
   * @param musicianProfileId target musician profile id
   * @param newLevel new Enigma level 0..5
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return stored level change
   * @throws org.springframework.web.server.ResponseStatusException 400 failed verification, 409
   *     signature already recorded
   */
  MusicianLevelChange confirmLevel(String walletPubkey, Long musicianProfileId, Integer newLevel, String txSignature);
}
