package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Builds governance multisig instructions (the backend never signs).
 */
public interface IGovernanceIxBuilder {

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
  Map<String, Object> buildInit(
      String programId, String rpcUrl, String ownerWallet, List<String> signers, int threshold);

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
  Map<String, Object> buildPropose(
      String programId,
      String rpcUrl,
      String proposerWallet,
      long proposalId,
      int kind,
      long amountAtomic,
      String target,
      int permissions,
      List<String> newSigners,
      int newThreshold);

  /**
   * Builds {@code approve_proposal()}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param approverWallet governance signer
   * @param proposalId on-chain proposal id
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildApprove(
      String programId, String rpcUrl, String approverWallet, long proposalId);

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
  Map<String, Object> buildExecuteWithdraw(
      String programId,
      String rpcUrl,
      String usdcMint,
      String executorWallet,
      long proposalId,
      String destinationUsdc);

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
  Map<String, Object> buildExecuteAgent(
      String programId, String rpcUrl, String executorWallet, long proposalId, String agentWallet);

  /**
   * Builds {@code execute_signers_proposal()}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param executorWallet any wallet (signer)
   * @param proposalId on-chain proposal id
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildExecuteSigners(
      String programId, String rpcUrl, String executorWallet, long proposalId);
}
