package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IGovernanceIxBuilder;
import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
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
import com.eh8s.eh8s.service.interfaces.IGovernanceService;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.AgentAuthorityIxBuilder;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder.OnchainGovernance;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.WithdrawTreasuryIxBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner multisig: {@code init_governance} (owner), {@code propose} / {@code approve_proposal}
 * (signers), {@code execute_*_proposal} (anyone once approvals reach the threshold). Every
 * confirm re-reads the DevNet transaction before a row is written.
 */
@Service
public class GovernanceService implements IGovernanceService {

  static final String KIND_WITHDRAW = "withdraw";
  static final String KIND_AGENT = "authorize_agent";
  static final String KIND_SIGNERS = "update_signers";

  private final IGovernanceRepository repository;
  private final IAgentAuthorityRepository agentRepository;
  private final IGovernanceIxBuilder ixBuilder;
  private final ISolanaRpcClient rpc;
  private final ISolanaTxVerifier txVerifier;

  /**
   * Creates the service.
   *
   * @param repository governance persistence
   * @param agentRepository agent roster and authorizations (agent proposals)
   * @param ixBuilder governance instruction builder
   * @param rpc DevNet RPC (governance account, treasury balance)
   * @param txVerifier DevNet transaction verifier
   */
  public GovernanceService(
      IGovernanceRepository repository,
      IAgentAuthorityRepository agentRepository,
      IGovernanceIxBuilder ixBuilder,
      ISolanaRpcClient rpc,
      ISolanaTxVerifier txVerifier) {
    this.repository = repository;
    this.agentRepository = agentRepository;
    this.ixBuilder = ixBuilder;
    this.rpc = rpc;
    this.txVerifier = txVerifier;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> overview(String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    String wallet = requireWallet(walletPubkey, "walletPubkey");
    String programId = cfg.getProgramIdDevnet();
    Governance gov = repository.findGovernance(programId).orElse(null);
    List<String> signers = gov == null ? List.of() : signerWallets(gov);
    boolean owner = wallet.equals(cfg.getOwnerWalletPubkey());
    if (!owner && !signers.contains(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner or a governance signer can open governance");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("programId", programId);
    out.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    out.put("governancePda", GovernanceIxBuilder.governancePda(programId));
    out.put("initialized", gov != null);
    out.put("signerRole", signers.contains(wallet) ? "signer" : "owner");
    out.put("maxSigners", GovernanceIxBuilder.MAX_SIGNERS);
    out.put("governance", gov);
    out.put("signers", signers);
    List<Map<String, Object>> proposals = new ArrayList<>();
    if (gov != null) {
      for (GovernanceProposal p : repository.findProposals(gov.getId())) {
        List<String> approvals =
            repository.findApprovals(p.getId()).stream().map(GovernanceApproval::getSignerWallet).toList();
        long counted = approvals.stream().filter(signers::contains).count();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("proposal", p);
        row.put("approvals", approvals);
        row.put("approvalCount", counted);
        row.put("approvedByMe", approvals.contains(wallet));
        row.put("executable", isOpen(p, gov) && counted >= gov.getThreshold());
        proposals.add(row);
      }
    }
    out.put("proposals", proposals);
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildInit(
      String walletPubkey, List<String> rawSigners, Integer rawThreshold) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String programId = cfg.getProgramIdDevnet();
    if (repository.findGovernance(programId).isPresent()
        || rpc.getAccountData(cfg.getRpcUrl(), GovernanceIxBuilder.governancePda(programId)) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Governance is already initialized");
    }
    List<String> signers = requireSignerSet(rawSigners, "rawSigners");
    int threshold = requireThreshold(rawThreshold, "rawThreshold", signers.size());
    return ixBuilder.buildInit(programId, cfg.getRpcUrl(), owner, signers, threshold);
  }

  /** {@inheritDoc} */
  @Override
  public Governance confirmInit(
      String walletPubkey, List<String> rawSigners, Integer rawThreshold, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    String owner = requireOwner(cfg, walletPubkey);
    String programId = cfg.getProgramIdDevnet();
    if (repository.findGovernance(programId).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Governance is already recorded");
    }
    List<String> signers = requireSignerSet(rawSigners, "rawSigners");
    int threshold = requireThreshold(rawThreshold, "rawThreshold", signers.size());
    String txSignature = requireFreshSignature(rawTxSignature);
    Map<String, Object> expected =
        ixBuilder.buildInit(programId, cfg.getRpcUrl(), owner, signers, threshold);
    String governancePda = GovernanceIxBuilder.governancePda(programId);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "init_governance",
        (String) expected.get("dataHex"),
        owner,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(programId), governancePda));
    LocalDateTime now = LocalDateTime.now();
    Governance row = new Governance();
    row.setProgramId(programId);
    row.setGovernancePda(governancePda);
    row.setThreshold((byte) threshold);
    row.setEpoch(0);
    row.setInitializedByWallet(owner);
    row.setInitTxSignature(txSignature);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    return repository.insertGovernance(row, signers);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildPropose(
      String walletPubkey,
      String kind,
      BigDecimal amountUsdc,
      String destinationWalletPubkey,
      String agentCode,
      String agentWalletPubkey,
      Integer permissions,
      List<String> newSigners,
      Integer newThreshold) {
    ChainConfig cfg = requireChainConfig();
    Governance gov = requireGovernance(cfg);
    String proposer = requireSigner(gov, walletPubkey);
    ProposalFields f = parseProposal(
            cfg, gov, walletPubkey, kind, amountUsdc, destinationWalletPubkey, agentCode,
            agentWalletPubkey, permissions, newSigners, newThreshold);
    if (f.kind == GovernanceIxBuilder.KIND_WITHDRAW) {
      requireTreasuryBalance(cfg, f.amountAtomic);
    }
    long proposalId = readOnchain(cfg).proposalCount();
    Map<String, Object> ix = buildProposeIx(cfg, proposer, proposalId, f);
    ix.put("kind", f.kindName);
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  public GovernanceProposal confirmPropose(
      String walletPubkey,
      String kind,
      BigDecimal amountUsdc,
      String destinationWalletPubkey,
      String agentCode,
      String agentWalletPubkey,
      Integer permissions,
      List<String> newSigners,
      Integer newThreshold,
      Long rawProposalId,
      String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    Governance gov = requireGovernance(cfg);
    String proposer = requireSigner(gov, walletPubkey);
    ProposalFields f = parseProposal(
            cfg, gov, walletPubkey, kind, amountUsdc, destinationWalletPubkey, agentCode,
            agentWalletPubkey, permissions, newSigners, newThreshold);
    long proposalId = requiredLong(rawProposalId, "rawProposalId");
    if (proposalId < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "proposalId must be >= 0");
    }
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected = buildProposeIx(cfg, proposer, proposalId, f);
    String proposalPda = GovernanceIxBuilder.proposalPda(programId, proposalId);
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "propose",
        (String) expected.get("dataHex"),
        proposer,
        ISolanaTxVerifier.accounts(
            null,
            /* #1 */ GovernanceIxBuilder.governancePda(programId),
            /* #2 */ proposalPda));
    GovernanceProposal row = new GovernanceProposal();
    row.setGovernanceId(gov.getId());
    row.setOnchainProposalId(proposalId);
    row.setProposalPda(proposalPda);
    row.setEpoch(gov.getEpoch());
    row.setKind(f.kindName);
    row.setProposerWallet(proposer);
    if (f.kind == GovernanceIxBuilder.KIND_WITHDRAW) {
      row.setAmountUsdc(BigDecimal.valueOf(f.amountAtomic, 6));
    }
    row.setTargetPubkey(f.target);
    if (f.kind == GovernanceIxBuilder.KIND_AUTHORIZE_AGENT) {
      row.setPermissions((byte) f.permissions);
      row.setAgentCode(f.agentCode);
    }
    if (f.kind == GovernanceIxBuilder.KIND_UPDATE_SIGNERS) {
      row.setNewSignersCsv(String.join(",", f.newSigners));
      row.setNewThreshold((byte) f.newThreshold);
    }
    row.setStatus("open");
    row.setProposeTxSignature(txSignature);
    row.setCreatedAt(LocalDateTime.now());
    return repository.insertProposal(row);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildApprove(Long proposalId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    Governance gov = requireGovernance(cfg);
    GovernanceProposal p = requireOpenProposal(gov, proposalId);
    String approver = requireSigner(gov, walletPubkey);
    requireNotApproved(p, approver);
    return ixBuilder.buildApprove(
        cfg.getProgramIdDevnet(), cfg.getRpcUrl(), approver, p.getOnchainProposalId());
  }

  /** {@inheritDoc} */
  @Override
  public GovernanceApproval confirmApprove(
      Long proposalId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    Governance gov = requireGovernance(cfg);
    GovernanceProposal p = requireOpenProposal(gov, proposalId);
    String approver = requireSigner(gov, walletPubkey);
    requireNotApproved(p, approver);
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected =
        ixBuilder.buildApprove(programId, cfg.getRpcUrl(), approver, p.getOnchainProposalId());
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        "approve_proposal",
        (String) expected.get("dataHex"),
        approver,
        ISolanaTxVerifier.accounts(
            null,
            /* #1 */ GovernanceIxBuilder.governancePda(programId),
            /* #2 */ p.getProposalPda()));
    GovernanceApproval row = new GovernanceApproval();
    row.setProposalId(p.getId());
    row.setSignerWallet(approver);
    row.setTxSignature(txSignature);
    row.setCreatedAt(LocalDateTime.now());
    return repository.insertApproval(row);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> buildExecute(Long proposalId, String walletPubkey) {
    ChainConfig cfg = requireChainConfig();
    Governance gov = requireGovernance(cfg);
    GovernanceProposal p = requireExecutable(gov, proposalId);
    String executor = requireWallet(requiredString(walletPubkey, "walletPubkey"), "walletPubkey");
    if (KIND_WITHDRAW.equals(p.getKind())) {
      requireTreasuryBalance(cfg, WithdrawTreasuryIxBuilder.toAtomic(p.getAmountUsdc()));
    }
    Map<String, Object> ix = buildExecuteIx(cfg, executor, p);
    ix.put("kind", p.getKind());
    return ix;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public GovernanceProposal confirmExecute(
      Long proposalId, String walletPubkey, String rawTxSignature) {
    ChainConfig cfg = requireChainConfig();
    Governance gov = requireGovernance(cfg);
    GovernanceProposal p = requireExecutable(gov, proposalId);
    String executor = requireWallet(requiredString(walletPubkey, "walletPubkey"), "walletPubkey");
    String txSignature = requireFreshSignature(rawTxSignature);
    String programId = cfg.getProgramIdDevnet();
    Map<String, Object> expected = buildExecuteIx(cfg, executor, p);
    String governancePda = GovernanceIxBuilder.governancePda(programId);
    List<String> accounts =
        switch (p.getKind()) {
          case KIND_WITHDRAW -> ISolanaTxVerifier.accounts(
              executor,
              SolanaPda.configPda(programId),
              governancePda,
              p.getProposalPda(),
              SolanaPda.treasuryPda(programId),
              SolanaPda.associatedTokenAddress(SolanaPda.treasuryPda(programId), cfg.getUsdcMint()),
              p.getTargetPubkey(),
              SolanaPda.TOKEN_PROGRAM);
          case KIND_AGENT ->
              ISolanaTxVerifier.accounts(
                  null,
                  /* #1 */ governancePda,
                  /* #2 */ p.getProposalPda(),
                  /* #3 */ (String) expected.get("agentAuthorityPda"));
          default -> ISolanaTxVerifier.accounts(null, governancePda, p.getProposalPda());
        };
    txVerifier.verify(
        cfg.getRpcUrl(),
        programId,
        txSignature,
        (String) expected.get("instruction"),
        (String) expected.get("dataHex"),
        executor,
        accounts);
    LocalDateTime now = LocalDateTime.now();
    p.setStatus("executed");
    p.setExecuteTxSignature(txSignature);
    p.setExecutedByWallet(executor);
    p.setExecutedAt(now);
    switch (p.getKind()) {
      case KIND_WITHDRAW -> {
        TreasuryWithdrawal w = new TreasuryWithdrawal();
        w.setOwnerWalletPubkey(signerOfUsdc(cfg, gov, p.getTargetPubkey()));
        w.setAmountUsdc(p.getAmountUsdc());
        w.setTxSignature(txSignature);
        w.setProposalId(p.getId());
        w.setCreatedAt(now);
        repository.recordWithdrawExecution(p, w);
      }
      case KIND_AGENT -> {
        OpsAgent agent = requireAgent(p.getAgentCode());
        AgentAuthority a = new AgentAuthority();
        a.setOpsAgentId(agent.getId());
        a.setAgentWalletPubkey(p.getTargetPubkey());
        a.setPermissions(p.getPermissions());
        a.setAgentAuthorityPda((String) expected.get("agentAuthorityPda"));
        a.setOwnerWalletPubkey(governancePda);
        a.setTxSignature(txSignature);
        a.setCreatedAt(now);
        agentRepository.insertAuthorization(a);
        repository.markExecuted(p);
      }
      default ->
          repository.recordSignersExecution(
              p, gov, Arrays.asList(p.getNewSignersCsv().split(",")), p.getNewThreshold());
    }
    return p;
  }

  private Map<String, Object> buildProposeIx(
      ChainConfig cfg, String proposer, long proposalId, ProposalFields f) {
    return ixBuilder.buildPropose(
        cfg.getProgramIdDevnet(),
        cfg.getRpcUrl(),
        proposer,
        proposalId,
        f.kind,
        f.amountAtomic,
        f.target,
        f.permissions,
        f.newSigners,
        f.newThreshold);
  }

  private Map<String, Object> buildExecuteIx(
      ChainConfig cfg, String executor, GovernanceProposal p) {
    String programId = cfg.getProgramIdDevnet();
    long id = p.getOnchainProposalId();
    return switch (p.getKind()) {
      case KIND_WITHDRAW ->
          ixBuilder.buildExecuteWithdraw(
              programId, cfg.getRpcUrl(), cfg.getUsdcMint(), executor, id, p.getTargetPubkey());
      case KIND_AGENT ->
          ixBuilder.buildExecuteAgent(programId, cfg.getRpcUrl(), executor, id, p.getTargetPubkey());
      default -> ixBuilder.buildExecuteSigners(programId, cfg.getRpcUrl(), executor, id);
    };
  }

  private ProposalFields parseProposal(
      ChainConfig cfg,
      Governance gov,
      String walletPubkey,
      String kind,
      BigDecimal amountUsdc,
      String destinationWalletPubkey,
      String agentCode,
      String agentWalletPubkey,
      Integer permissions,
      List<String> newSigners,
      Integer newThreshold) {
    String kindName = requiredString(kind, "kind");
    ProposalFields f = new ProposalFields();
    f.kindName = kindName;
    switch (kindName) {
      case KIND_WITHDRAW -> {
        f.kind = GovernanceIxBuilder.KIND_WITHDRAW;
        f.amountAtomic = requireAmount(amountUsdc);
        String dest =
            blank(destinationWalletPubkey)
                ? requiredString(walletPubkey, "walletPubkey")
                : destinationWalletPubkey.trim();
        dest = requireWallet(dest, "destinationWalletPubkey");
        if (!signerWallets(gov).contains(dest)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "The destination must be a governance signer");
        }
        f.target = SolanaPda.associatedTokenAddress(dest, cfg.getUsdcMint());
      }
      case KIND_AGENT -> {
        f.kind = GovernanceIxBuilder.KIND_AUTHORIZE_AGENT;
        f.agentCode = requireAgent(requiredString(agentCode, "agentCode")).getCode();
        f.target = requireWallet(requiredString(agentWalletPubkey, "agentWalletPubkey"), "agentWalletPubkey");
        long bits = requiredLong(permissions, "permissions");
        if (bits < 0 || (bits & ~(long) AgentAuthorityIxBuilder.PERM_ALL) != 0) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "permissions must only use bits 0x01..0x10");
        }
        f.permissions = (int) bits;
      }
      case KIND_SIGNERS -> {
        f.kind = GovernanceIxBuilder.KIND_UPDATE_SIGNERS;
        f.newSigners = requireSignerSet(newSigners, "newSigners");
        f.newThreshold = requireThreshold(newThreshold, "newThreshold", f.newSigners.size());
      }
      default ->
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "kind must be withdraw, authorize_agent, or update_signers");
    }
    return f;
  }

  private OnchainGovernance readOnchain(ChainConfig cfg) {
    byte[] data =
        rpc.getAccountData(
            cfg.getRpcUrl(), GovernanceIxBuilder.governancePda(cfg.getProgramIdDevnet()));
    if (data == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The governance account is not on DevNet yet");
    }
    try {
      return GovernanceIxBuilder.decodeGovernance(data);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
  }

  private void requireTreasuryBalance(ChainConfig cfg, long amountAtomic) {
    String treasuryUsdc =
        SolanaPda.associatedTokenAddress(
            SolanaPda.treasuryPda(cfg.getProgramIdDevnet()), cfg.getUsdcMint());
    long balance = rpc.getTokenAccountBalance(cfg.getRpcUrl(), treasuryUsdc);
    if (amountAtomic > balance) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Treasury holds " + BigDecimal.valueOf(balance, 6).toPlainString() + " USDC");
    }
  }

  private String signerOfUsdc(ChainConfig cfg, Governance gov, String usdcAccount) {
    return signerWallets(gov).stream()
        .filter(s -> SolanaPda.associatedTokenAddress(s, cfg.getUsdcMint()).equals(usdcAccount))
        .findFirst()
        .orElse(usdcAccount);
  }

  private GovernanceProposal requireOpenProposal(Governance gov, Long proposalId) {
    GovernanceProposal p =
        repository
            .findProposal(proposalId)
            .filter(x -> x.getGovernanceId().equals(gov.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown proposal"));
    if (!"open".equals(p.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal is " + p.getStatus());
    }
    if (!isOpen(p, gov)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The signer set changed after this proposal; open a new one");
    }
    return p;
  }

  private GovernanceProposal requireExecutable(Governance gov, Long proposalId) {
    GovernanceProposal p = requireOpenProposal(gov, proposalId);
    List<String> signers = signerWallets(gov);
    long counted =
        repository.findApprovals(p.getId()).stream()
            .map(GovernanceApproval::getSignerWallet)
            .filter(signers::contains)
            .count();
    if (counted < gov.getThreshold()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Proposal has " + counted + " of " + gov.getThreshold() + " approvals");
    }
    return p;
  }

  private void requireNotApproved(GovernanceProposal p, String wallet) {
    boolean already =
        repository.findApprovals(p.getId()).stream().anyMatch(a -> wallet.equals(a.getSignerWallet()));
    if (already) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This signer already approved");
    }
  }

  private static boolean isOpen(GovernanceProposal p, Governance gov) {
    return "open".equals(p.getStatus()) && p.getEpoch().equals(gov.getEpoch());
  }

  private List<String> signerWallets(Governance gov) {
    return repository.findSigners(gov.getId()).stream().map(GovernanceSigner::getWalletPubkey).toList();
  }

  private Governance requireGovernance(ChainConfig cfg) {
    return repository
        .findGovernance(cfg.getProgramIdDevnet())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.CONFLICT, "Governance is not initialized"));
  }

  private String requireSigner(Governance gov, String walletPubkey) {
    String wallet = requireWallet(requiredString(walletPubkey, "walletPubkey"), "walletPubkey");
    if (!signerWallets(gov).contains(wallet)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wallet is not a governance signer");
    }
    return wallet;
  }

  private OpsAgent requireAgent(String code) {
    return agentRepository
        .findAgentByCode(code.toUpperCase())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown agent " + code));
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

  private static String requireOwner(ChainConfig cfg, String walletPubkey) {
    String wallet = requireWallet(requiredString(walletPubkey, "walletPubkey"), "walletPubkey");
    if (!cfg.getOwnerWalletPubkey().equals(wallet)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the platform owner wallet can initialize governance");
    }
    return wallet;
  }

  private static List<String> requireSignerSet(List<String> list, String key) {
    if (list == null || list.isEmpty() || list.size() > GovernanceIxBuilder.MAX_SIGNERS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must list 1..5 wallets");
    }
    List<String> out = new ArrayList<>();
    for (String o : list) {
      out.add(requireWallet(o == null ? null : o.trim(), key));
    }
    if (new HashSet<>(out).size() != out.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be distinct wallets");
    }
    return List.copyOf(out);
  }

  private static int requireThreshold(Integer value, String key, int signers) {
    long t = requiredLong(value, key);
    if (t < 1 || t > signers) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be 1.." + signers);
    }
    return (int) t;
  }

  private static long requireAmount(BigDecimal amountUsdc) {
    if (amountUsdc == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountUsdc is required");
    }
    long atomic;
    try {
      atomic = WithdrawTreasuryIxBuilder.toAtomic(amountUsdc);
    } catch (ArithmeticException ex) {
      atomic = 0;
    }
    if (atomic <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountUsdc must be > 0");
    }
    return atomic;
  }

  private static String requireWallet(String wallet, String key) {
    if (blank(wallet)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
    byte[] raw;
    try {
      raw = SolanaPda.decode(wallet.trim());
    } catch (RuntimeException ex) {
      raw = null;
    }
    if (raw == null || raw.length != 32 || Arrays.equals(raw, new byte[32])) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a base58 wallet");
    }
    return wallet.trim();
  }

  private static long requiredLong(Number value, String key) {
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

  private static final class ProposalFields {
    String kindName;
    int kind;
    long amountAtomic;
    String target;
    int permissions;
    String agentCode;
    List<String> newSigners = List.of();
    int newThreshold;
  }
}
