package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceSigner;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ persistence for the program v0.8.0 owner multisig: governance, its signers, proposals,
 * and approvals. Rows are written only after the DevNet verifier accepts the transaction.
 */
public interface IGovernanceRepository {

  /**
   * Active chain configuration (program id, mint, RPC, owner wallet).
   *
   * @return active chain_config row, if any
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * The recorded governance for a program, if {@code init_governance} was verified.
   *
   * @param programId base58 program id
   * @return governance row, if any
   */
  Optional<Governance> findGovernance(String programId);

  /**
   * Current signers in on-chain order.
   *
   * @param governanceId governance id
   * @return signer rows ordered by position
   */
  List<GovernanceSigner> findSigners(Long governanceId);

  /**
   * Stores a verified governance and its signer set.
   *
   * @param governance governance row (id ignored)
   * @param signers signer wallets in on-chain order
   * @return stored governance with id
   */
  Governance insertGovernance(Governance governance, List<String> signers);

  /**
   * Proposals of a governance, newest first.
   *
   * @param governanceId governance id
   * @return proposal rows
   */
  List<GovernanceProposal> findProposals(Long governanceId);

  /**
   * One proposal by id.
   *
   * @param proposalId governance_proposal id
   * @return proposal, if any
   */
  Optional<GovernanceProposal> findProposal(Long proposalId);

  /**
   * Approvals of a proposal, oldest first.
   *
   * @param proposalId governance_proposal id
   * @return approval rows
   */
  List<GovernanceApproval> findApprovals(Long proposalId);

  /**
   * Stores a verified proposal plus the proposer's approval (same transaction signature).
   *
   * @param proposal proposal row (id ignored)
   * @return stored proposal with id
   */
  GovernanceProposal insertProposal(GovernanceProposal proposal);

  /**
   * Stores a verified approval.
   *
   * @param approval approval row (id ignored)
   * @return stored approval with id
   */
  GovernanceApproval insertApproval(GovernanceApproval approval);

  /**
   * Marks a proposal executed.
   *
   * @param proposal proposal with {@code executeTxSignature}, {@code executedByWallet},
   *     {@code executedAt} set
   */
  void markExecuted(GovernanceProposal proposal);

  /**
   * Marks a withdraw proposal executed and records the matching treasury withdrawal.
   *
   * @param proposal executed proposal (execute fields set)
   * @param withdrawal withdrawal row linked to the proposal
   * @return stored withdrawal with id
   */
  TreasuryWithdrawal recordWithdrawExecution(GovernanceProposal proposal, TreasuryWithdrawal withdrawal);

  /**
   * Marks a signer proposal executed, replaces the signer set, sets the threshold, bumps the
   * epoch, and marks older open proposals stale.
   *
   * @param proposal executed proposal (execute fields set)
   * @param governance governance row to update
   * @param newSigners new signer wallets in order
   * @param newThreshold new threshold
   * @return updated governance
   */
  Governance recordSignersExecution(
      GovernanceProposal proposal, Governance governance, List<String> newSigners, int newThreshold);

  /**
   * Whether a signature is already recorded anywhere in governance.
   *
   * @param txSignature base58 signature
   * @return true when stored
   */
  boolean isRecorded(String txSignature);
}
