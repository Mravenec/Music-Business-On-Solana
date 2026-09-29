package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IGovernanceIxBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the program v0.8.0 governance instructions ({@code init_governance}, {@code propose},
 * {@code approve_proposal}, {@code execute_*_proposal}) and decodes the {@code ["governance"]}
 * account. The backend never signs; wallets sign what these payloads describe.
 */
@Component
public class GovernanceIxBuilder implements IGovernanceIxBuilder {

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";

  /** Proposal kind: treasury PDA ATA to a signer's USDC account. */
  public static final int KIND_WITHDRAW = 0;
  /** Proposal kind: grant, change, or revoke agent bits. */
  public static final int KIND_AUTHORIZE_AGENT = 1;
  /** Proposal kind: replace the signer set and threshold. */
  public static final int KIND_UPDATE_SIGNERS = 2;
  /** Program limit on governance signers. */
  public static final int MAX_SIGNERS = 5;

  /**
   * Decoded {@code Governance} account.
   *
   * @param signers current signer wallets in order
   * @param threshold approvals needed to execute
   * @param epoch signer-set generation
   * @param proposalCount next proposal id
   */
  public record OnchainGovernance(List<String> signers, int threshold, long epoch, long proposalCount) {}

  /**
   * Governance PDA {@code ["governance"]}.
   *
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String governancePda(String programId) {
    return SolanaPda.findProgramAddress(List.of(SolanaPda.seed("governance")), programId);
  }

  /**
   * Proposal PDA {@code ["proposal", id LE]}.
   *
   * @param programId base58 program id
   * @param proposalId on-chain proposal id
   * @return base58 PDA
   */
  public static String proposalPda(String programId, long proposalId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("proposal"), SolanaPda.u64le(proposalId)), programId);
  }

  /**
   * Decodes the Anchor {@code Governance} account (8-byte discriminator, Borsh fields).
   *
   * @param data raw account data
   * @return decoded governance
   * @throws IllegalArgumentException when the data is too short
   */
  public static OnchainGovernance decodeGovernance(byte[] data) {
    ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    try {
      buf.position(8);
      int n = buf.getInt();
      if (n < 0 || n > MAX_SIGNERS) {
        throw new IllegalArgumentException("Governance signer count " + n);
      }
      List<String> signers = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        byte[] key = new byte[32];
        buf.get(key);
        signers.add(SolanaPda.encode(key));
      }
      int threshold = Byte.toUnsignedInt(buf.get());
      long epoch = Integer.toUnsignedLong(buf.getInt());
      long proposalCount = buf.getLong();
      return new OnchainGovernance(List.copyOf(signers), threshold, epoch, proposalCount);
    } catch (java.nio.BufferUnderflowException ex) {
      throw new IllegalArgumentException("Governance account data is too short", ex);
    }
  }

  /**
   * Builds {@code init_governance(signers, threshold)} for the config owner.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays rent)
   * @param signers 1..5 distinct signer wallets
   * @param threshold approvals needed (1..signers)
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildInit(
      String programId, String rpcUrl, String ownerWallet, List<String> signers, int threshold) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("init_governance");
    ByteBuffer data = ByteBuffer.allocate(8 + 4 + 32 * signers.size() + 1).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    putPubkeys(data, signers);
    data.put((byte) threshold);

    String governance = governancePda(programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("governance", governance, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body = payload("init_governance", programId, rpcUrl, disc, data, accounts);
    body.put("governancePda", governance);
    body.put("signers", signers);
    body.put("threshold", threshold);
    body.put(
        "note",
        "Owner signs init_governance: from now on treasury withdrawals and agent powers need "
            + threshold
            + " of "
            + signers.size()
            + " signer approvals.");
    return body;
  }

  /**
   * Builds {@code propose(kind, amount, target, permissions, new_signers, new_threshold)}.
   * Unused fields for a kind are sent empty (the program stores them empty anyway).
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param proposerWallet governance signer (signer, pays rent)
   * @param proposalId on-chain {@code governance.proposal_count}
   * @param kind {@link #KIND_WITHDRAW}, {@link #KIND_AUTHORIZE_AGENT}, or {@link
   *     #KIND_UPDATE_SIGNERS}
   * @param amountAtomic withdraw amount (6-decimal atomic), else 0
   * @param target withdraw destination USDC account or agent wallet, else null
   * @param permissions agent bits, else 0
   * @param newSigners signer-set proposal wallets, else empty
   * @param newThreshold signer-set proposal threshold, else 0
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildPropose(
      String programId,
      String rpcUrl,
      String proposerWallet,
      long proposalId,
      int kind,
      long amountAtomic,
      String target,
      int permissions,
      List<String> newSigners,
      int newThreshold) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("propose");
    ByteBuffer data =
        ByteBuffer.allocate(8 + 1 + 8 + 32 + 1 + 4 + 32 * newSigners.size() + 1)
            .order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.put((byte) kind);
    data.putLong(amountAtomic);
    data.put(target == null ? new byte[32] : SolanaPda.decode(target));
    data.put((byte) permissions);
    putPubkeys(data, newSigners);
    data.put((byte) newThreshold);

    String governance = governancePda(programId);
    String proposal = proposalPda(programId, proposalId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("proposer", proposerWallet, true, true));
    accounts.add(account("governance", governance, true, false));
    accounts.add(account("proposal", proposal, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body = payload("propose", programId, rpcUrl, disc, data, accounts);
    body.put("governancePda", governance);
    body.put("proposalId", proposalId);
    body.put("proposalPda", proposal);
    body.put("note", "Signer opens the proposal; their approval is recorded with it.");
    return body;
  }

  /**
   * Builds {@code approve_proposal()}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param approverWallet governance signer
   * @param proposalId on-chain proposal id
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildApprove(
      String programId, String rpcUrl, String approverWallet, long proposalId) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("approve_proposal");
    ByteBuffer data = ByteBuffer.allocate(8).put(disc);
    String governance = governancePda(programId);
    String proposal = proposalPda(programId, proposalId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("approver", approverWallet, false, true));
    accounts.add(account("governance", governance, false, false));
    accounts.add(account("proposal", proposal, true, false));

    Map<String, Object> body = payload("approve_proposal", programId, rpcUrl, disc, data, accounts);
    body.put("governancePda", governance);
    body.put("proposalId", proposalId);
    body.put("proposalPda", proposal);
    body.put("note", "Signer approves the proposal once.");
    return body;
  }

  /**
   * Builds {@code execute_withdraw_proposal()}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint configured USDC mint
   * @param executorWallet any wallet (signer)
   * @param proposalId on-chain proposal id
   * @param destinationUsdc proposal target (a signer's USDC account)
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildExecuteWithdraw(
      String programId,
      String rpcUrl,
      String usdcMint,
      String executorWallet,
      long proposalId,
      String destinationUsdc) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("execute_withdraw_proposal");
    ByteBuffer data = ByteBuffer.allocate(8).put(disc);
    String treasuryPda = SolanaPda.treasuryPda(programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("executor", executorWallet, false, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("governance", governancePda(programId), false, false));
    accounts.add(account("proposal", proposalPda(programId, proposalId), true, false));
    accounts.add(account("treasuryAuthority", treasuryPda, false, false));
    accounts.add(
        account("treasuryUsdc", SolanaPda.associatedTokenAddress(treasuryPda, usdcMint), true, false));
    accounts.add(account("destinationUsdc", destinationUsdc, true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));

    Map<String, Object> body =
        payload("execute_withdraw_proposal", programId, rpcUrl, disc, data, accounts);
    body.put("proposalId", proposalId);
    body.put("destinationUsdc", destinationUsdc);
    body.put("note", "Runs the approved withdraw: the treasury PDA pays the signer's USDC account.");
    return body;
  }

  /**
   * Builds {@code execute_agent_proposal()}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param executorWallet any wallet (signer, pays AgentAuthority rent when new)
   * @param proposalId on-chain proposal id
   * @param agentWallet proposal target agent wallet
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildExecuteAgent(
      String programId, String rpcUrl, String executorWallet, long proposalId, String agentWallet) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("execute_agent_proposal");
    ByteBuffer data = ByteBuffer.allocate(8).put(disc);
    String agentPda = SolanaPda.walletPda("agent", agentWallet, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("executor", executorWallet, true, true));
    accounts.add(account("governance", governancePda(programId), false, false));
    accounts.add(account("proposal", proposalPda(programId, proposalId), true, false));
    accounts.add(account("agentAuthority", agentPda, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body =
        payload("execute_agent_proposal", programId, rpcUrl, disc, data, accounts);
    body.put("proposalId", proposalId);
    body.put("agentAuthorityPda", agentPda);
    body.put("note", "Runs the approved agent proposal: the AgentAuthority PDA gets the new bits.");
    return body;
  }

  /**
   * Builds {@code execute_signers_proposal()}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param executorWallet any wallet (signer)
   * @param proposalId on-chain proposal id
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildExecuteSigners(
      String programId, String rpcUrl, String executorWallet, long proposalId) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("execute_signers_proposal");
    ByteBuffer data = ByteBuffer.allocate(8).put(disc);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("executor", executorWallet, false, true));
    accounts.add(account("governance", governancePda(programId), true, false));
    accounts.add(account("proposal", proposalPda(programId, proposalId), true, false));

    Map<String, Object> body =
        payload("execute_signers_proposal", programId, rpcUrl, disc, data, accounts);
    body.put("proposalId", proposalId);
    body.put("note", "Runs the approved signer change; older open proposals become stale.");
    return body;
  }

  private static void putPubkeys(ByteBuffer data, List<String> keys) {
    data.putInt(keys.size());
    for (String key : keys) {
      data.put(SolanaPda.decode(key));
    }
  }

  private static Map<String, Object> payload(
      String instruction,
      String programId,
      String rpcUrl,
      byte[] disc,
      ByteBuffer data,
      List<Map<String, Object>> accounts) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", instruction);
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("discriminatorHex", HexFormat.of().formatHex(disc));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    return body;
  }

  private static Map<String, Object> account(
      String name, String pubkey, boolean writable, boolean signer) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("pubkey", pubkey);
    row.put("isWritable", writable);
    row.put("isSigner", signer);
    return row;
  }
}
