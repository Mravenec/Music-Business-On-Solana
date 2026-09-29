package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ENIGMA_LEVEL;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_LEVEL_CHANGE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.GOVERNANCE;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.AGENT_AUTHORITY;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.OPS_AGENT;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s.tables.records.MusicianLevelChangeRecord;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.records.AgentAuthorityRecord;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOOQ persistence for agent authority and agent-only Enigma level changes.
 */
@Repository
public class AgentAuthorityRepository implements IAgentAuthorityRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public AgentAuthorityRepository(DSLContext dsl) {
    this.dsl = dsl;
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
  public List<OpsAgent> findAgents() {
    return dsl.selectFrom(OPS_AGENT).orderBy(OPS_AGENT.ID).fetchInto(OpsAgent.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<OpsAgent> findAgentByCode(String code) {
    return dsl.selectFrom(OPS_AGENT).where(OPS_AGENT.CODE.eq(code)).fetchOptionalInto(OpsAgent.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<AgentAuthority> findAuthorizations() {
    return dsl.selectFrom(AGENT_AUTHORITY)
        .orderBy(AGENT_AUTHORITY.ID.desc())
        .fetchInto(AgentAuthority.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<AgentAuthority> findLatestAuthorization(String agentWallet) {
    return dsl.selectFrom(AGENT_AUTHORITY)
        .where(AGENT_AUTHORITY.AGENT_WALLET_PUBKEY.eq(agentWallet))
        .orderBy(AGENT_AUTHORITY.ID.desc())
        .limit(1)
        .fetchOptionalInto(AgentAuthority.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public AgentAuthority insertAuthorization(AgentAuthority row) {
    AgentAuthorityRecord rec = dsl.newRecord(AGENT_AUTHORITY, row);
    rec.changed(AGENT_AUTHORITY.ID, false);
    rec.store();
    boolean revoke = row.getPermissions() == null || row.getPermissions() == 0;
    dsl.update(OPS_AGENT)
        .set(OPS_AGENT.WALLET_PUBKEY, revoke ? null : row.getAgentWalletPubkey())
        .where(OPS_AGENT.ID.eq(row.getOpsAgentId()))
        .and(
            revoke
                ? OPS_AGENT.WALLET_PUBKEY.eq(row.getAgentWalletPubkey())
                : DSL.noCondition())
        .execute();
    return rec.into(AgentAuthority.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<MusicianProfile> findLevelTargetProfiles() {
    return dsl.select(MUSICIAN_PROFILE.fields())
        .from(MUSICIAN_PROFILE)
        .join(ACCOUNT)
        .on(ACCOUNT.ID.eq(MUSICIAN_PROFILE.ACCOUNT_ID))
        .where(ACCOUNT.WALLET_PUBKEY.isNotNull())
        .and(MUSICIAN_PROFILE.ENIGMA_LEVEL_ID.isNotNull())
        .orderBy(MUSICIAN_PROFILE.ID)
        .fetchInto(MusicianProfile.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<MusicianProfile> findMusicianProfile(Long musicianProfileId) {
    return dsl.selectFrom(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ID.eq(musicianProfileId))
        .fetchOptionalInto(MusicianProfile.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<Account> findAccountsByIds(List<Long> accountIds) {
    if (accountIds == null || accountIds.isEmpty()) {
      return List.of();
    }
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.in(accountIds)).fetchInto(Account.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<EnigmaLevel> findEnigmaLevels() {
    return dsl.selectFrom(ENIGMA_LEVEL)
        .orderBy(ENIGMA_LEVEL.LEVEL_NUMBER)
        .fetchInto(EnigmaLevel.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Long> findEnigmaLevelId(int levelNumber) {
    return dsl.select(ENIGMA_LEVEL.ID)
        .from(ENIGMA_LEVEL)
        .where(ENIGMA_LEVEL.LEVEL_NUMBER.eq((byte) levelNumber))
        .fetchOptional(ENIGMA_LEVEL.ID);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public MusicianLevelChange recordLevelChange(MusicianLevelChange row, Long enigmaLevelId) {
    MusicianLevelChangeRecord rec = dsl.newRecord(MUSICIAN_LEVEL_CHANGE, row);
    rec.changed(MUSICIAN_LEVEL_CHANGE.ID, false);
    rec.store();
    dsl.update(MUSICIAN_PROFILE)
        .set(MUSICIAN_PROFILE.ENIGMA_LEVEL_ID, enigmaLevelId)
        .where(MUSICIAN_PROFILE.ID.eq(row.getMusicianProfileId()))
        .execute();
    return rec.into(MusicianLevelChange.class);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecorded(String txSignature) {
    return dsl.fetchExists(AGENT_AUTHORITY, AGENT_AUTHORITY.TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(MUSICIAN_LEVEL_CHANGE, MUSICIAN_LEVEL_CHANGE.TX_SIGNATURE.eq(txSignature));
  }

  /** {@inheritDoc} */
  @Override
  public boolean governanceActive() {
    return dsl.fetchExists(GOVERNANCE);
  }
}
