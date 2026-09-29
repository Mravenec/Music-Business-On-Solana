package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IAgentAuthorityIxBuilder;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.service.interfaces.IAgentAuthorityService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner grants agent permission bits on-chain; the owner or a pedagogical agent changes Enigma
 * levels on-chain. The backend never signs: it builds instructions and verifies signatures.
 */
@Service
public class AgentAuthorityService implements IAgentAuthorityService {

  static final List<Map<String, Object>> PERMISSION_BITS =
      List.of(
          bit(AgentAuthorityIxBuilder.PERM_PEDAGOGICAL, "pedagogical", "NEXUS sets Enigma levels"),
          bit(AgentAuthorityIxBuilder.PERM_HARMONY, "harmony", "Band vaults and SPP weights"),
          bit(AgentAuthorityIxBuilder.PERM_STAGE, "stage", "Venue approval and booking confirmation"),
          bit(AgentAuthorityIxBuilder.PERM_VAULT, "vault", "Settlement from booking escrow"),
          bit(AgentAuthorityIxBuilder.PERM_WAVE, "wave", "Royalty deposits"));

  private final IAgentAuthorityRepository repository;
  private final IAgentAuthorityIxBuilder ixBuilder;
  private final ISolanaTxVerifier txVerifier;

  /**
   * Creates the service.
   *
   * @param repository agents, authorizations, musician levels
   * @param ixBuilder authorize_agent / update_musician_level builder
   * @param txVerifier DevNet transaction verifier
   */
  public AgentAuthorityService(
      IAgentAuthorityRepository repository,
      IAgentAuthorityIxBuilder ixBuilder,
      ISolanaTxVerifier txVerifier) {
    this.repository = repository;
    this.ixBuilder = ixBuilder;
    this.txVerifier = txVerifier;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> roster(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    requireOwner(cfg, walletPubkey);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    out.put("programId", cfg.getProgramIdDevnet());
    out.put("permissionBits", PERMISSION_BITS);
    out.put("agents", repository.findAgents());
    out.put("authorizations", repository.findAuthorizations());
    out.put("governanceActive", repository.governanceActive());
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildAuthorize(
      String walletPubkey, String agentCode, String agentWalletPubkey, Integer rawPermissions) {
    ChainConfig cfg = requireChainConfig();
    String owner = requiredString(walletPubkey, "walletPubkey");
    requireOwner(cfg, owner);
    if (repository.governanceActive()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Governance is active: open an agent proposal instead");
    }
    OpsAgent agent = requireAgent(requiredString(agentCode, "agentCode"));
    String agentWallet = requireWallet(requiredString(agentWalletPubkey, "agentWalletPubkey"));
    int permissions = requirePermissions(rawPermissions);
    Map<String, Object> ix =
        ixBuilder.buildAuthorize(
            cfg.getProgramIdDevnet(), cfg.getRpcUrl(), owner, agentWallet, permissions);
    ix.put("agentCode", agent.getCode());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public AgentAuthority confirmAuthorize(
      String walletPubkey,
      String agentCode,
      String agentWalletPubkey,
      Integer rawPermissions,
      String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String owner = requiredString(walletPubkey, "walletPubkey");
    requireOwner(cfg, owner);
    OpsAgent agent = requireAgent(requiredString(agentCode, "agentCode"));
    String agentWallet = requireWallet(requiredString(agentWalletPubkey, "agentWalletPubkey"));
    int permissions = requirePermissions(rawPermissions);
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildAuthorize(programId, cfg.getRpcUrl(), owner, agentWallet, permissions);
    String agentPda = (String) expected.get("agentAuthorityPda");
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "authorize_agent",
        (String) expected.get("dataHex"),
        owner,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), agentPda));
    AgentAuthority row = new AgentAuthority();
    row.setOpsAgentId(agent.getId());
    row.setAgentWalletPubkey(agentWallet);
    row.setPermissions((byte) permissions);
    row.setAgentAuthorityPda(agentPda);
    row.setOwnerWalletPubkey(owner);
    row.setTxSignature(txSignature);
    row.setCreatedAt(LocalDateTime.now());
    return repository.insertAuthorization(row);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> levelTargets(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    boolean agentSigner = requireLevelPower(cfg, walletPubkey);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("signerWalletPubkey", walletPubkey.trim());
    out.put("signerRole", agentSigner ? "pedagogical_agent" : "owner");
    out.put("maxLevel", AgentAuthorityIxBuilder.MAX_LEVEL);
    out.put("musicians", levelTargetRows());
    return out;
  }

  /** One UI row per target: profile id, account name/wallet, Enigma level, country. */
  private List<Map<String, Object>> levelTargetRows() {
    List<MusicianProfile> profiles = repository.findLevelTargetProfiles();
    Map<Long, Account> accounts = new HashMap<>();
    for (Account a :
        repository.findAccountsByIds(
            profiles.stream().map(MusicianProfile::getAccountId).distinct().toList())) {
      accounts.put(a.getId(), a);
    }
    Map<Long, EnigmaLevel> levels = new HashMap<>();
    for (EnigmaLevel l : repository.findEnigmaLevels()) {
      levels.put(l.getId(), l);
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MusicianProfile p : profiles) {
      Account a = accounts.get(p.getAccountId());
      EnigmaLevel l = levels.get(p.getEnigmaLevelId());
      if (a == null || blank(a.getWalletPubkey()) || l == null) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("musicianProfileId", p.getId());
      row.put("displayName", a.getDisplayName());
      row.put("walletPubkey", a.getWalletPubkey());
      row.put("levelNumber", l.getLevelNumber());
      row.put("levelName", l.getName());
      row.put("countryCode", p.getCountryCode());
      rows.add(row);
    }
    return rows;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildLevel(
      String walletPubkey, Long musicianProfileId, Integer rawNewLevel) {
    ChainConfig cfg = requireChainConfig();
    String signer = requiredString(walletPubkey, "walletPubkey");
    boolean agentSigner = requireLevelPower(cfg, signer);
    int newLevel = requireLevel(rawNewLevel);
    MusicianProfile target = requireTarget(musicianProfileId);
    Map<String, Object> ix =
        ixBuilder.buildLevel(
            cfg.getProgramIdDevnet(),
            cfg.getRpcUrl(),
            signer,
            walletOf(target),
            newLevel,
            agentSigner);
    ix.put("musicianProfileId", target.getId());
    ix.put("currentLevel", levelOf(target).getLevelNumber());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public MusicianLevelChange confirmLevel(
      String walletPubkey, Long musicianProfileId, Integer rawNewLevel, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String signer = requiredString(walletPubkey, "walletPubkey");
    boolean agentSigner = requireLevelPower(cfg, signer);
    int newLevel = requireLevel(rawNewLevel);
    MusicianProfile target = requireTarget(musicianProfileId);
    Long levelId =
        repository
            .findEnigmaLevelId(newLevel)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Enigma level " + newLevel + " not seeded"));
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildLevel(
            programId,
            cfg.getRpcUrl(),
            signer,
            walletOf(target),
            newLevel,
            agentSigner);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "update_musician_level",
        (String) expected.get("dataHex"),
        signer,
        ISolanaTxVerifier.accounts(
            null,
            /* #1 */ SolanaPda.configPda(programId),
            /* #2 */ (String) expected.get("musicianProfilePda"),
            /* #3 */ (String) expected.get("agentAuthorityAccount")));
    MusicianLevelChange row = new MusicianLevelChange();
    row.setMusicianProfileId(target.getId());
    row.setAgentWalletPubkey(signer);
    Byte current = levelOf(target).getLevelNumber();
    row.setOldLevel(current == null ? 0 : current);
    row.setNewLevel((byte) newLevel);
    row.setTxSignature(txSignature);
    row.setCreatedAt(LocalDateTime.now());
    return repository.recordLevelChange(row, levelId);
  }

  /** Owner → false (no AgentAuthority account); live 0x01 agent → true; otherwise 403. */
  private boolean requireLevelPower(ChainConfig cfg, String walletPubkey) {
    if (blank(walletPubkey)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    String wallet = walletPubkey.trim();
    if (cfg.getOwnerWalletPubkey().equals(wallet)) {
      return false;
    }
    boolean pedagogical =
        repository
            .findLatestAuthorization(wallet)
            .map(a -> a.getPermissions() != null
                && (a.getPermissions() & AgentAuthorityIxBuilder.PERM_PEDAGOGICAL) != 0)
            .orElse(false);
    if (!pedagogical) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner or a pedagogical (0x01) agent can change levels");
    }
    return true;
  }

  private MusicianProfile requireTarget(Long musicianProfileId) {
    Long profileId = requiredLong(musicianProfileId, "musicianProfileId");
    MusicianProfile target =
        repository
            .findMusicianProfile(profileId)
            .filter(p -> p.getEnigmaLevelId() != null)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown musician"));
    if (blank(walletOf(target))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Musician has no wallet (no on-chain profile)");
    }
    return target;
  }

  private String walletOf(MusicianProfile profile) {
    return repository.findAccountsByIds(List.of(profile.getAccountId())).stream()
        .findFirst()
        .map(Account::getWalletPubkey)
        .orElse(null);
  }

  private EnigmaLevel levelOf(MusicianProfile profile) {
    return repository.findEnigmaLevels().stream()
        .filter(l -> l.getId().equals(profile.getEnigmaLevelId()))
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown musician"));
  }

  private OpsAgent requireAgent(String code) {
    return repository
        .findAgentByCode(code.toUpperCase())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown agent " + code));
  }

  private String requireFreshSignature(String rawSignature) {
    String txSignature = requiredString(rawSignature, "txSignature");
    if (repository.isRecorded(txSignature)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Signature already recorded");
    }
    return txSignature;
  }

  private ChainConfig requireChainConfig() {
    ChainConfig cfg =
        repository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain_config"));
    if (blank(cfg.getOwnerWalletPubkey())) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "chain_config has no owner wallet");
    }
    return cfg;
  }

  private static void requireOwner(ChainConfig cfg, String walletPubkey) {
    if (blank(walletPubkey)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    if (!cfg.getOwnerWalletPubkey().equals(walletPubkey.trim())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the platform owner wallet can authorize agents");
    }
  }

  private static String requireWallet(String wallet) {
    byte[] raw;
    try {
      raw = SolanaPda.decode(wallet);
    } catch (RuntimeException ex) {
      raw = null;
    }
    boolean allZero = raw != null && Arrays.equals(raw, new byte[32]);
    if (raw == null || raw.length != 32 || allZero) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "agentWalletPubkey must be a base58 wallet");
    }
    return wallet;
  }

  private static int requirePermissions(Integer permissions) {
    Long raw = requiredLong(permissions, "permissions");
    if (raw < 0 || (raw & ~(long) AgentAuthorityIxBuilder.PERM_ALL) != 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "permissions must only use bits 0x01..0x10");
    }
    return raw.intValue();
  }

  private static int requireLevel(Integer newLevel) {
    Long raw = requiredLong(newLevel, "newLevel");
    if (raw < 0 || raw > AgentAuthorityIxBuilder.MAX_LEVEL) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newLevel must be 0..5");
    }
    return raw.intValue();
  }

  private static Long requiredLong(Number value, String key) {
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return value.longValue();
  }

  private static String requiredString(String value, String key) {
    String trimmed = value == null ? null : value.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    return trimmed;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static Map<String, Object> bit(int mask, String name, String power) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("mask", mask);
    row.put("name", name);
    row.put("power", power);
    return row;
  }
}
