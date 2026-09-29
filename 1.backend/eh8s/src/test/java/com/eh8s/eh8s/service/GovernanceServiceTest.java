package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceSigner;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.AgentAuthority;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.repository.interfaces.IAgentAuthorityRepository;
import com.eh8s.eh8s.repository.interfaces.IGovernanceRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder.OnchainGovernance;
import com.eh8s.eh8s.service.solana.SettleClaimIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

class GovernanceServiceTest {

  static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  static final String MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";
  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String S1 = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";
  static final String S2 = "9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z";
  static final String STRANGER = "U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq";
  static final String AGENT = "CoP8dydvHXTDUoxxB6tAQk6yu3Q6YRkqNj8SY3vtR53o";
  static final String CONFIG_PDA = "GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF";

  private Governance governance;
  private final List<String> signers = new ArrayList<>();
  private final List<GovernanceProposal> proposals = new ArrayList<>();
  private final List<GovernanceApproval> approvals = new ArrayList<>();
  private final List<TreasuryWithdrawal> withdrawals = new ArrayList<>();
  private final List<AgentAuthority> authorizations = new ArrayList<>();
  private final Map<String, Object> verified = new HashMap<>();
  private byte[] onchain;
  private long treasuryAtomic = 50_000_000L;

  @Test
  void builderDecodesGovernanceAndPdas() {
    OnchainGovernance g = GovernanceIxBuilder.decodeGovernance(account(List.of(S1, S2), 2, 3, 7));
    assertEquals(List.of(S1, S2), g.signers());
    assertEquals(2, g.threshold());
    assertEquals(3L, g.epoch());
    assertEquals(7L, g.proposalCount());
    assertEquals("3ZH4igyjF1jFPhyRTAczh1Za8bgjaTfXe2cB9v2v49XY", GovernanceIxBuilder.governancePda(PROGRAM));
    assertThrows(IllegalArgumentException.class, () -> GovernanceIxBuilder.decodeGovernance(new byte[10]));
  }

  @Test
  void builderEncodesInitAndPropose() {
    GovernanceIxBuilder b = new GovernanceIxBuilder();
    Map<String, Object> init = b.buildInit(PROGRAM, "rpc", OWNER, List.of(S1), 1);
    String disc = hex(SettleClaimIxBuilder.anchorDiscriminator("init_governance"));
    assertEquals(disc + "01000000" + hex(SolanaPda.decode(S1)) + "01", init.get("dataHex"));
    Map<String, Object> prop =
        b.buildPropose(PROGRAM, "rpc", S1, 4, GovernanceIxBuilder.KIND_AUTHORIZE_AGENT, 0, AGENT, 3, List.of(), 0);
    String pdisc = hex(SettleClaimIxBuilder.anchorDiscriminator("propose"));
    assertEquals(
        pdisc + "01" + "0000000000000000" + hex(SolanaPda.decode(AGENT)) + "03" + "00000000" + "00",
        prop.get("dataHex"));
    assertEquals(GovernanceIxBuilder.proposalPda(PROGRAM, 4), prop.get("proposalPda"));
  }

  @Test
  void overviewIsOwnerOrSignerOnly() {
    GovernanceService s = service();
    assertStatus(HttpStatus.FORBIDDEN, () -> s.overview(STRANGER));
    assertStatus(HttpStatus.BAD_REQUEST, () -> s.overview(null));
    assertEquals(false, s.overview(OWNER).get("initialized"));
    seedGovernance(List.of(S1, S2), 2);
    assertEquals("signer", s.overview(S1).get("signerRole"));
    assertStatus(HttpStatus.FORBIDDEN, () -> s.overview(STRANGER));
  }

  @Test
  void buildInitValidatesOwnerSignerSetAndThreshold() {
    GovernanceService s = service();
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildInit(s, initBody(S1, List.of(S1), 1)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildInit(s, initBody(OWNER, List.of(), 1)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildInit(s, initBody(OWNER, List.of(S1, S1), 1)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildInit(s, initBody(OWNER, List.of(S1, S2), 3)));
    assertStatus(
        HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildInit(s, initBody(OWNER, List.of(S1, S2, OWNER, AGENT, STRANGER, CONFIG_PDA), 1)));
    assertEquals("init_governance", OnchainCalls.buildInit(s, initBody(OWNER, List.of(S1, S2), 2)).get("instruction"));
    onchain = account(List.of(S1), 1, 0, 0);
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildInit(s, initBody(OWNER, List.of(S1, S2), 2)));
  }

  @Test
  void confirmInitVerifiesAccountsThenStoresSigners() {
    GovernanceService s = service();
    Map<String, Object> body = initBody(OWNER, List.of(S1, S2), 2);
    body.put("txSignature", "sig-init");
    Governance g = OnchainCalls.confirmInit(s, body);
    assertEquals("init_governance", verified.get("instruction"));
    assertEquals(OWNER, verified.get("signer"));
    assertEquals(ISolanaTxVerifier.accounts(
        null,
        /* #1 */ CONFIG_PDA,
        /* #2 */ GovernanceIxBuilder.governancePda(PROGRAM)), verified.get("accounts"));
    assertEquals((byte) 2, g.getThreshold());
    assertEquals(List.of(S1, S2), signers);
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.confirmInit(s, body));
  }

  @Test
  void proposeNeedsGovernanceSignerAndValidKind() {
    GovernanceService s = service();
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildPropose(s, withdrawBody(S1, "5")));
    seedGovernance(List.of(S1, S2), 2);
    assertStatus(HttpStatus.FORBIDDEN, () -> OnchainCalls.buildPropose(s, withdrawBody(STRANGER, "5")));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildPropose(s, withdrawBody(S1, "0")));
    Map<String, Object> bad = withdrawBody(S1, "5");
    bad.put("kind", "burn");
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildPropose(s, bad));
    Map<String, Object> toStranger = withdrawBody(S1, "5");
    toStranger.put("destinationWalletPubkey", STRANGER);
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildPropose(s, toStranger));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildPropose(s, withdrawBody(S1, "500")));
    Map<String, Object> ix = OnchainCalls.buildPropose(s, withdrawBody(S1, "5"));
    assertEquals(0L, ix.get("proposalId"));
    assertEquals("withdraw", ix.get("kind"));
    assertStatus(HttpStatus.NOT_FOUND, () -> OnchainCalls.buildPropose(s, agentBody(S1, "GHOST", 1)));
    assertStatus(HttpStatus.BAD_REQUEST, () -> OnchainCalls.buildPropose(s, agentBody(S1, "NEXUS", 0x20)));
  }

  @Test
  void fullWithdrawFlowReachesThresholdThenExecutes() {
    GovernanceService s = service();
    seedGovernance(List.of(S1, S2), 2);
    Map<String, Object> pb = withdrawBody(S1, "5");
    pb.put("proposalId", 0);
    pb.put("txSignature", "sig-propose");
    GovernanceProposal p = OnchainCalls.confirmPropose(s, pb);
    assertEquals("propose", verified.get("instruction"));
    assertEquals(new BigDecimal("5.000000"), p.getAmountUsdc());
    assertEquals(SolanaPda.associatedTokenAddress(S1, MINT), p.getTargetPubkey());
    assertEquals(1, approvals.size());

    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildExecute(s, p.getId(), wallet(STRANGER)));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildApprove(s, p.getId(), wallet(S1)));
    Map<String, Object> ab = wallet(S2);
    ab.put("txSignature", "sig-approve");
    OnchainCalls.confirmApprove(s, p.getId(), ab);
    assertEquals(
        ISolanaTxVerifier.accounts(
            null,
            /* #1 */ GovernanceIxBuilder.governancePda(PROGRAM),
            /* #2 */ p.getProposalPda()), verified.get("accounts"));

    assertEquals("execute_withdraw_proposal", OnchainCalls.buildExecute(s, p.getId(), wallet(STRANGER)).get("instruction"));
    Map<String, Object> eb = wallet(STRANGER);
    eb.put("txSignature", "sig-exec");
    GovernanceProposal done = OnchainCalls.confirmExecute(s, p.getId(), eb);
    assertEquals("executed", done.getStatus());
    assertEquals(S1, withdrawals.get(0).getOwnerWalletPubkey());
    assertEquals(p.getId(), withdrawals.get(0).getProposalId());
    assertEquals(p.getTargetPubkey(), ((List<?>) verified.get("accounts")).get(6));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildExecute(s, p.getId(), wallet(STRANGER)));
  }

  @Test
  void agentProposalRecordsAuthorizationUnderGovernance() {
    GovernanceService s = service();
    seedGovernance(List.of(S1), 1);
    Map<String, Object> pb = agentBody(S1, "nexus", 3);
    pb.put("proposalId", 0);
    pb.put("txSignature", "sig-p-agent");
    GovernanceProposal p = OnchainCalls.confirmPropose(s, pb);
    assertEquals("NEXUS", p.getAgentCode());
    Map<String, Object> eb = wallet(S1);
    eb.put("txSignature", "sig-e-agent");
    OnchainCalls.confirmExecute(s, p.getId(), eb);
    assertEquals("execute_agent_proposal", verified.get("instruction"));
    AgentAuthority a = authorizations.get(0);
    assertEquals(GovernanceIxBuilder.governancePda(PROGRAM), a.getOwnerWalletPubkey());
    assertEquals(SolanaPda.walletPda("agent", AGENT, PROGRAM), a.getAgentAuthorityPda());
    assertEquals((byte) 3, a.getPermissions());
  }

  @Test
  void signersProposalBumpsEpochAndStalesOlderProposals() {
    GovernanceService s = service();
    seedGovernance(List.of(S1), 1);
    Map<String, Object> older = withdrawBody(S1, "1");
    older.put("proposalId", 0);
    older.put("txSignature", "sig-older");
    GovernanceProposal stale = OnchainCalls.confirmPropose(s, older);

    Map<String, Object> pb = new LinkedHashMap<>();
    pb.put("walletPubkey", S1);
    pb.put("kind", "update_signers");
    pb.put("newSigners", List.of(S1, S2));
    pb.put("newThreshold", 2);
    pb.put("proposalId", 1);
    pb.put("txSignature", "sig-p-signers");
    GovernanceProposal p = OnchainCalls.confirmPropose(s, pb);
    Map<String, Object> eb = wallet(S1);
    eb.put("txSignature", "sig-e-signers");
    OnchainCalls.confirmExecute(s, p.getId(), eb);
    assertEquals("execute_signers_proposal", verified.get("instruction"));
    assertEquals(1, governance.getEpoch());
    assertEquals(List.of(S1, S2), signers);
    assertEquals("stale", stale.getStatus());
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.buildApprove(s, stale.getId(), wallet(S2)));
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.confirmExecute(s, p.getId(), eb));
  }

  @Test
  void replayedSignatureIsRejected() {
    GovernanceService s = service();
    seedGovernance(List.of(S1, S2), 2);
    Map<String, Object> pb = withdrawBody(S1, "5");
    pb.put("proposalId", 0);
    pb.put("txSignature", "seed-init");
    assertStatus(HttpStatus.CONFLICT, () -> OnchainCalls.confirmPropose(s, pb));
    assertTrue(proposals.isEmpty());
  }

  private void seedGovernance(List<String> wallets, int threshold) {
    governance = new Governance();
    governance.setId(1L);
    governance.setProgramId(PROGRAM);
    governance.setGovernancePda(GovernanceIxBuilder.governancePda(PROGRAM));
    governance.setThreshold((byte) threshold);
    governance.setEpoch(0);
    governance.setInitTxSignature("seed-init");
    signers.clear();
    signers.addAll(wallets);
    onchain = account(wallets, threshold, 0, proposals.size());
  }

  private static byte[] account(List<String> wallets, int threshold, int epoch, long count) {
    ByteBuffer buf = ByteBuffer.allocate(8 + 4 + 32 * wallets.size() + 1 + 4 + 8 + 1).order(ByteOrder.LITTLE_ENDIAN);
    buf.put(new byte[8]);
    buf.putInt(wallets.size());
    wallets.forEach(w -> buf.put(SolanaPda.decode(w)));
    buf.put((byte) threshold);
    buf.putInt(epoch);
    buf.putLong(count);
    buf.put((byte) 255);
    return buf.array();
  }

  private static Map<String, Object> initBody(String wallet, List<String> set, int threshold) {
    Map<String, Object> body = wallet(wallet);
    body.put("signers", set);
    body.put("threshold", threshold);
    return body;
  }

  private static Map<String, Object> withdrawBody(String wallet, String amount) {
    Map<String, Object> body = wallet(wallet);
    body.put("kind", "withdraw");
    body.put("amountUsdc", amount);
    return body;
  }

  private static Map<String, Object> agentBody(String wallet, String code, int bits) {
    Map<String, Object> body = wallet(wallet);
    body.put("kind", "authorize_agent");
    body.put("agentCode", code);
    body.put("agentWalletPubkey", AGENT);
    body.put("permissions", bits);
    return body;
  }

  private static Map<String, Object> wallet(String wallet) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("walletPubkey", wallet);
    return body;
  }

  private static String hex(byte[] b) {
    return HexFormat.of().formatHex(b);
  }

  private static void assertStatus(HttpStatus status, Runnable call) {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
    assertEquals(status, ex.getStatusCode());
  }

  private GovernanceService service() {
    return new GovernanceService(repo(), agentRepo(), new GovernanceIxBuilder(), rpc(), verifier());
  }

  private SolanaRpcClient rpc() {
    return new SolanaRpcClient() {
      @Override
      public long getTokenAccountBalance(String rpcUrl, String tokenAccount) {
        return treasuryAtomic;
      }

      @Override
      public byte[] getAccountData(String rpcUrl, String address) {
        return onchain;
      }
    };
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

  private IGovernanceRepository repo() {
    return new IGovernanceRepository() {
      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        ChainConfig cfg = new ChainConfig();
        cfg.setProgramIdDevnet(PROGRAM);
        cfg.setRpcUrl("https://api.devnet.solana.com");
        cfg.setUsdcMint(MINT);
        cfg.setOwnerWalletPubkey(OWNER);
        return Optional.of(cfg);
      }

      @Override
      public Optional<Governance> findGovernance(String programId) {
        return Optional.ofNullable(governance);
      }

      @Override
      public List<GovernanceSigner> findSigners(Long governanceId) {
        List<GovernanceSigner> out = new ArrayList<>();
        for (String w : signers) {
          GovernanceSigner row = new GovernanceSigner();
          row.setGovernanceId(governanceId);
          row.setWalletPubkey(w);
          out.add(row);
        }
        return out;
      }

      @Override
      public Governance insertGovernance(Governance g, List<String> wallets) {
        g.setId(1L);
        governance = g;
        signers.clear();
        signers.addAll(wallets);
        return g;
      }

      @Override
      public List<GovernanceProposal> findProposals(Long governanceId) {
        return proposals;
      }

      @Override
      public Optional<GovernanceProposal> findProposal(Long proposalId) {
        return proposals.stream().filter(p -> p.getId().equals(proposalId)).findFirst();
      }

      @Override
      public List<GovernanceApproval> findApprovals(Long proposalId) {
        return approvals.stream().filter(a -> a.getProposalId().equals(proposalId)).toList();
      }

      @Override
      public GovernanceProposal insertProposal(GovernanceProposal p) {
        p.setId(100L + proposals.size());
        proposals.add(p);
        GovernanceApproval a = new GovernanceApproval();
        a.setProposalId(p.getId());
        a.setSignerWallet(p.getProposerWallet());
        a.setTxSignature(p.getProposeTxSignature());
        approvals.add(a);
        onchain = account(signers, governance.getThreshold(), governance.getEpoch(), proposals.size());
        return p;
      }

      @Override
      public GovernanceApproval insertApproval(GovernanceApproval a) {
        approvals.add(a);
        return a;
      }

      @Override
      public void markExecuted(GovernanceProposal p) {
        p.setStatus("executed");
      }

      @Override
      public TreasuryWithdrawal recordWithdrawExecution(GovernanceProposal p, TreasuryWithdrawal w) {
        markExecuted(p);
        withdrawals.add(w);
        return w;
      }

      @Override
      public Governance recordSignersExecution(
          GovernanceProposal p, Governance g, List<String> wallets, int threshold) {
        markExecuted(p);
        g.setThreshold((byte) threshold);
        g.setEpoch(g.getEpoch() + 1);
        signers.clear();
        signers.addAll(wallets);
        proposals.stream().filter(x -> "open".equals(x.getStatus())).forEach(x -> x.setStatus("stale"));
        return g;
      }

      @Override
      public boolean isRecorded(String tx) {
        return (governance != null && tx.equals(governance.getInitTxSignature()))
            || proposals.stream()
                .anyMatch(p -> tx.equals(p.getProposeTxSignature()) || tx.equals(p.getExecuteTxSignature()))
            || approvals.stream().anyMatch(a -> tx.equals(a.getTxSignature()));
      }
    };
  }

  private IAgentAuthorityRepository agentRepo() {
    return new IAgentAuthorityRepository() {
      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        return Optional.empty();
      }

      @Override
      public List<OpsAgent> findAgents() {
        OpsAgent a = new OpsAgent();
        a.setId(1L);
        a.setCode("NEXUS");
        a.setName("NEXUS");
        return List.of(a);
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
        return Optional.empty();
      }

      @Override
      public AgentAuthority insertAuthorization(AgentAuthority row) {
        authorizations.add(0, row);
        return row;
      }

      @Override
      public List<com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile>
          findLevelTargetProfiles() {
        return List.of();
      }

      @Override
      public Optional<com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile>
          findMusicianProfile(Long musicianProfileId) {
        return Optional.empty();
      }

      @Override
      public List<com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account> findAccountsByIds(
          List<Long> accountIds) {
        return List.of();
      }

      @Override
      public List<com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel> findEnigmaLevels() {
        return List.of();
      }

      @Override
      public Optional<Long> findEnigmaLevelId(int levelNumber) {
        return Optional.empty();
      }

      @Override
      public MusicianLevelChange recordLevelChange(MusicianLevelChange row, Long enigmaLevelId) {
        return row;
      }

      @Override
      public boolean isRecorded(String txSignature) {
        return false;
      }
    };
  }
}
