package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.SettleClaimIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AgentAuthorityServiceTest {

  static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String NEXUS = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";
  static final String HARMONY = "9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z";
  static final String STRANGER = "U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq";
  static final String MUSICIAN = "CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o";
  static final String CONFIG_PDA = "GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF";

  private final List<AgentAuthority> authorizations = new ArrayList<>();
  private final List<MusicianLevelChange> changes = new ArrayList<>();
  private final Map<String, Object> verified = new HashMap<>();
  private Long movedToLevelId;

  @Test
  void builderEncodesAuthorizeAndLevel() {
    AgentAuthorityIxBuilder b = new AgentAuthorityIxBuilder();
    Map<String, Object> auth = b.buildAuthorize(PROGRAM, "rpc", OWNER, NEXUS, 0x03);
    String disc = HexFormat.of().formatHex(SettleClaimIxBuilder.anchorDiscriminator("authorize_agent"));
    assertEquals(disc + HexFormat.of().formatHex(SolanaPda.decode(NEXUS)) + "03", auth.get("dataHex"));
    assertEquals(SolanaPda.walletPda("agent", NEXUS, PROGRAM), auth.get("agentAuthorityPda"));
    List<?> accounts = (List<?>) auth.get("accounts");
    assertEquals(5, accounts.size());
    assertEquals(GovernanceIxBuilder.governancePda(PROGRAM), ((Map<?, ?>) accounts.get(4)).get("pubkey"));

    Map<String, Object> asOwner = b.buildLevel(PROGRAM, "rpc", OWNER, MUSICIAN, 4, false);
    assertTrue(((String) asOwner.get("dataHex")).endsWith("04"));
    assertEquals(PROGRAM, asOwner.get("agentAuthorityAccount"));
    Map<String, Object> asAgent = b.buildLevel(PROGRAM, "rpc", NEXUS, MUSICIAN, 4, true);
    assertEquals(SolanaPda.walletPda("agent", NEXUS, PROGRAM), asAgent.get("agentAuthorityAccount"));
    assertEquals(SolanaPda.walletPda("musician", MUSICIAN, PROGRAM), asAgent.get("musicianProfilePda"));
  }

  @Test
  void rosterIsOwnerOnly() {
    AgentAuthorityService s = service();
    assertStatus(HttpStatus.FORBIDDEN, () -> s.roster(STRANGER));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.roster(null));
    Map<String, Object> view = s.roster(OWNER);
    assertEquals(5, ((List<?>) view.get("permissionBits")).size());
    assertEquals(2, ((List<?>) view.get("agents")).size());
  }

  @Test
  void buildAuthorizeValidatesOwnerBitsWalletAndAgent() {
    AgentAuthorityService s = service();
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildAuthorize(s, authBody(STRANGER, "NEXUS", NEXUS, 1)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildAuthorize(s, authBody(OWNER, "NEXUS", NEXUS, 0x20)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildAuthorize(s, authBody(OWNER, "NEXUS", "not-a-wallet", 1)));
    assertStatus(
        HttpStatus.BAD_REQUEST,
        () -> OnchainCalls.buildAuthorize(s, authBody(OWNER, "NEXUS", "11111111111111111111111111111111", 1)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildAuthorize(s, authBody(OWNER, "GHOST", NEXUS, 1)));
    Map<String, Object> ix = OnchainCalls.buildAuthorize(s, authBody(OWNER, "nexus", NEXUS, 1));
    assertEquals("NEXUS", ix.get("agentCode"));
    assertEquals("authorize_agent", ix.get("instruction"));
  }

  @Test
  void confirmAuthorizeVerifiesThenRecordsAndRejectsReplay() {
    AgentAuthorityService s = service();
    Map<String, Object> body = authBody(OWNER, "NEXUS", NEXUS, 1);
    body.put("txSignature", "sig-auth");
    AgentAuthority row = OnchainCalls.confirmAuthorize(s, body);
    assertEquals("authorize_agent", verified.get("instruction"));
    assertEquals(OWNER, verified.get("signer"));
    assertEquals(
        ISolanaTxVerifier.accounts(null, CONFIG_PDA, SolanaPda.walletPda("agent", NEXUS, PROGRAM)), verified.get("accounts"));
    assertEquals((byte) 1, row.getPermissions());
    assertEquals(1L, row.getOpsAgentId());
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.confirmAuthorize(s, body));
  }

  @Test
  void onlyOwnerOrLivePedagogicalAgentReachesLevels() {
    AgentAuthorityService s = service();
    grant(HARMONY, AgentAuthorityIxBuilder.PERM_HARMONY);
    assertStatus(HttpStatus.FORBIDDEN, () -> s.levelTargets(STRANGER));
    assertStatus(HttpStatus.FORBIDDEN, () -> s.levelTargets(HARMONY));
    assertEquals("owner", s.levelTargets(OWNER).get("signerRole"));
    grant(NEXUS, AgentAuthorityIxBuilder.PERM_PEDAGOGICAL);
    assertEquals("pedagogical_agent", s.levelTargets(NEXUS).get("signerRole"));
    grant(NEXUS, 0);
    assertStatus(HttpStatus.FORBIDDEN, () -> s.levelTargets(NEXUS));
  }

  @Test
  void buildLevelChecksRangeAndMusician() {
    AgentAuthorityService s = service();
    grant(NEXUS, AgentAuthorityIxBuilder.PERM_PEDAGOGICAL);
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildLevel(s, levelBody(NEXUS, 7L, 6)));
    assertStatus(HttpStatus.NOT_FOUND, () -> OnchainCalls.buildLevel(s, levelBody(NEXUS, 99L, 3)));
    Map<String, Object> ix = OnchainCalls.buildLevel(s, levelBody(NEXUS, 7L, 3));
    assertEquals(SolanaPda.walletPda("agent", NEXUS, PROGRAM), ix.get("agentAuthorityAccount"));
    assertEquals((byte) 1, ix.get("currentLevel"));
  }

  @Test
  void confirmLevelVerifiesAgentAccountThenMovesMusician() {
    AgentAuthorityService s = service();
    grant(NEXUS, AgentAuthorityIxBuilder.PERM_PEDAGOGICAL);
    Map<String, Object> body = levelBody(NEXUS, 7L, 3);
    body.put("txSignature", "sig-level");
    MusicianLevelChange row = OnchainCalls.confirmLevel(s, body);
    assertEquals("update_musician_level", verified.get("instruction"));
    assertEquals(NEXUS, verified.get("signer"));
    assertEquals(
        ISolanaTxVerifier.accounts(
            null,
            /* #1 */ CONFIG_PDA,
            /* #2 */ SolanaPda.walletPda("musician", MUSICIAN, PROGRAM),
            /* #3 */ SolanaPda.walletPda("agent", NEXUS, PROGRAM)),
        verified.get("accounts"));
    assertEquals((byte) 1, row.getOldLevel());
    assertEquals((byte) 3, row.getNewLevel());
    assertEquals(103L, movedToLevelId);
  }

  private void grant(String wallet, int bits) {
    AgentAuthority a = new AgentAuthority();
    a.setId((long) authorizations.size() + 1);
    a.setAgentWalletPubkey(wallet);
    a.setPermissions((byte) bits);
    a.setTxSignature("seed-" + authorizations.size());
    authorizations.add(0, a);
  }

  private static Map<String, Object> authBody(String owner, String code, String agent, int bits) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("walletPubkey", owner);
    body.put("agentCode", code);
    body.put("agentWalletPubkey", agent);
    body.put("permissions", bits);
    return body;
  }

  private static Map<String, Object> levelBody(String signer, Long profileId, int level) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("walletPubkey", signer);
    body.put("musicianProfileId", profileId);
    body.put("newLevel", level);
    return body;
  }

  private static void assertStatus(HttpStatus status, Runnable call) {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
    assertEquals(status, ex.getStatusCode());
  }

  private AgentAuthorityService service() {
    return new AgentAuthorityService(repo(), new AgentAuthorityIxBuilder(), verifier());
  }

  private ISolanaTxVerifier verifier() {
    return new ISolanaTxVerifier() {
      @Override
      public void verify(
          String rpcUrl,
          String programId,
          String txSignature,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        verified.put("instruction", instruction);
        verified.put("dataHex", expectedDataHex);
        verified.put("signer", expectedSigner);
        verified.put("accounts", expectedAccounts);
      }

      @Override
      public void verifyTransaction(
          JsonNode tx,
          String programId,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private IAgentAuthorityRepository repo() {
    return new IAgentAuthorityRepository() {
      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        ChainConfig cfg = new ChainConfig();
        cfg.setProgramIdDevnet(PROGRAM);
        cfg.setRpcUrl("https://api.devnet.solana.com");
        cfg.setOwnerWalletPubkey(OWNER);
        return Optional.of(cfg);
      }

      @Override
      public List<OpsAgent> findAgents() {
        return List.of(agent(1L, "NEXUS"), agent(3L, "HARMONY"));
      }

      @Override
      public Optional<OpsAgent> findAgentByCode(String code) {
        return findAgents().stream().filter(a -> a.getCode().equals(code)).findFirst();
      }

      @Override
      public List<AgentAuthority> findAuthorizations() {
        return authorizations;
      }

      @Override
      public Optional<AgentAuthority> findLatestAuthorization(String agentWallet) {
        return authorizations.stream()
            .filter(a -> a.getAgentWalletPubkey().equals(agentWallet))
            .findFirst();
      }

      @Override
      public AgentAuthority insertAuthorization(AgentAuthority row) {
        row.setId((long) authorizations.size() + 1);
        authorizations.add(0, row);
        return row;
      }

      @Override
      public List<MusicianProfile> findLevelTargetProfiles() {
        return List.of(musician());
      }

      @Override
      public Optional<MusicianProfile> findMusicianProfile(Long musicianProfileId) {
        return musicianProfileId == 7L ? Optional.of(musician()) : Optional.empty();
      }

      @Override
      public List<Account> findAccountsByIds(List<Long> accountIds) {
        return accountIds.contains(70L) ? List.of(musicianAccount()) : List.of();
      }

      @Override
      public List<EnigmaLevel> findEnigmaLevels() {
        return List.of(level());
      }

      @Override
      public Optional<Long> findEnigmaLevelId(int levelNumber) {
        return Optional.of(100L + levelNumber);
      }

      @Override
      public MusicianLevelChange recordLevelChange(MusicianLevelChange row, Long enigmaLevelId) {
        movedToLevelId = enigmaLevelId;
        changes.add(row);
        return row;
      }

      @Override
      public boolean isRecorded(String txSignature) {
        return authorizations.stream().anyMatch(a -> txSignature.equals(a.getTxSignature()))
            || changes.stream().anyMatch(c -> txSignature.equals(c.getTxSignature()));
      }
    };
  }

  private static OpsAgent agent(Long id, String code) {
    OpsAgent a = new OpsAgent();
    a.setId(id);
    a.setCode(code);
    a.setName(code);
    return a;
  }

  private static MusicianProfile musician() {
    MusicianProfile p = new MusicianProfile();
    p.setId(7L);
    p.setAccountId(70L);
    p.setEnigmaLevelId(101L);
    p.setCountryCode("MEX");
    return p;
  }

  private static Account musicianAccount() {
    Account a = new Account();
    a.setId(70L);
    a.setDisplayName("Test Musician");
    a.setWalletPubkey(MUSICIAN);
    return a;
  }

  private static EnigmaLevel level() {
    EnigmaLevel l = new EnigmaLevel();
    l.setId(101L);
    l.setLevelNumber((byte) 1);
    l.setName("Foundations");
    return l;
  }

  @Test
  void levelTargetsJoinProfileAccountAndLevel() {
    List<?> musicians = (List<?>) service().levelTargets(OWNER).get("musicians");
    assertEquals(
        Map.of(
            "musicianProfileId", 7L,
            "displayName", "Test Musician",
            "walletPubkey", MUSICIAN,
            "levelNumber", (byte) 1,
            "levelName", "Foundations",
            "countryCode", "MEX"),
        musicians.get(0));
  }
}
