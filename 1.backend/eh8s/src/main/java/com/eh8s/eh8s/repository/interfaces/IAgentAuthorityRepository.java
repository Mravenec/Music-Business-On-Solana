package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ persistence for on-chain agent authority (authorize_agent) and agent-only Enigma level
 * changes (update_musician_level).
 */
public interface IAgentAuthorityRepository {

  /**
   * Active chain configuration (program id, RPC, owner wallet).
   *
   * @return active chain_config row, if any
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Agent roster ordered by id.
   *
   * @return every ops_agent row
   */
  List<OpsAgent> findAgents();

  /**
   * One agent by its code (for example {@code NEXUS}).
   *
   * @param code agent code
   * @return matching agent, if any
   */
  Optional<OpsAgent> findAgentByCode(String code);

  /**
   * Verified authorize_agent rows, newest first.
   *
   * @return agent_authority rows
   */
  List<AgentAuthority> findAuthorizations();

  /**
   * Newest verified authorization for a wallet (the live on-chain bits).
   *
   * @param agentWallet agent wallet
   * @return newest row for that wallet, if any
   */
  Optional<AgentAuthority> findLatestAuthorization(String agentWallet);

  /**
   * Stores a verified authorization and links the wallet to its agent (cleared on revoke).
   *
   * @param row verified authorization
   * @return stored row with id
   */
  AgentAuthority insertAuthorization(AgentAuthority row);

  /**
   * Musician profiles that sit on an Enigma level and whose account has a wallet (targets for
   * update_musician_level), ordered by id.
   *
   * @return musician_profile rows
   */
  List<MusicianProfile> findLevelTargetProfiles();

  /**
   * One musician profile by id.
   *
   * @param musicianProfileId musician_profile id
   * @return profile, if found
   */
  Optional<MusicianProfile> findMusicianProfile(Long musicianProfileId);

  /**
   * Accounts by id (display name and wallet of each musician).
   *
   * @param accountIds account ids (empty list returns an empty list)
   * @return account rows found (any order)
   */
  List<Account> findAccountsByIds(List<Long> accountIds);

  /**
   * Every seeded Enigma level.
   *
   * @return enigma_level rows ordered by level number
   */
  List<EnigmaLevel> findEnigmaLevels();

  /**
   * Primary key of an Enigma level by its number (0..5).
   *
   * @param levelNumber level number
   * @return enigma_level id, if seeded
   */
  Optional<Long> findEnigmaLevelId(int levelNumber);

  /**
   * Stores a verified level change and moves the musician to the new level.
   *
   * @param row verified change
   * @param enigmaLevelId enigma_level id for {@code row.newLevel}
   * @return stored row with id
   */
  MusicianLevelChange recordLevelChange(MusicianLevelChange row, Long enigmaLevelId);

  /**
   * Whether a signature is already recorded as an authorization or level change.
   *
   * @param txSignature base58 signature
   * @return true when already stored
   */
  boolean isRecorded(String txSignature);

  /**
   * Whether a verified {@code init_governance} is recorded (direct authorize is then refused).
   *
   * @return true once governance exists
   */
  default boolean governanceActive() {
    return false;
  }
}
