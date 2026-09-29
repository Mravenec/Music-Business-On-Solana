package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.math.BigDecimal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import java.util.Map;

/**
 * Owner multisig (program v0.8.0): builds wallet-signed governance instructions and records them
 * after the DevNet verifier accepts the transaction. The backend never signs.
 */
public interface IGovernanceService {

  /**
   * Governance screen for the owner or a current signer: signer set, threshold, and proposals
   * with their approvals.
   *
   * @param walletPubkey connected wallet
   * @return governance view
   * @throws org.springframework.web.server.ResponseStatusException 400 missing wallet, 403 when
   *     the wallet is neither the owner nor a signer
   */
  Map<String, Object> overview(String walletPubkey);

  /**
   * Builds {@code init_governance} for the config owner.
   *
   * @param walletPubkey signer wallet (base58)
   * @param signers governance signer wallets (1..5, distinct)
   * @param threshold approvals required (1..signers)
   * @return instruction payload
   * @throws org.springframework.web.server.ResponseStatusException 400 bad signer set, 403 not
   *     the owner, 409 already initialized
   */
  Map<String, Object> buildInit(String walletPubkey, List<String> signers, Integer threshold);

  /**
   * Verifies an {@code init_governance} signature and records the governance.
   *
   * @param walletPubkey signer wallet (base58)
   * @param signers governance signer wallets (1..5, distinct)
   * @param threshold approvals required (1..signers)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return stored governance
   * @throws org.springframework.web.server.ResponseStatusException 400 verifier rejection, 409
   *     replayed signature
   */
  Governance confirmInit(String walletPubkey, List<String> signers, Integer threshold, String txSignature);

  /**
   * Builds {@code propose} for a current signer. Kinds: {@code withdraw} ({@code amountUsdc},
   * {@code destinationWalletPubkey} = a signer, default the proposer), {@code authorize_agent}
   * ({@code agentCode}, {@code agentWalletPubkey}, {@code permissions}), {@code update_signers}
   * ({@code newSigners}, {@code newThreshold}).
   *
   * @param walletPubkey signer wallet (base58)
   * @param kind withdraw, authorize_agent or update_signers
   * @param amountUsdc USDC amount
   * @param destinationWalletPubkey withdraw destination signer (defaults to walletPubkey)
   * @param agentCode ops agent code (for example NEXUS)
   * @param agentWalletPubkey agent signer wallet (base58)
   * @param permissions permission bitmask (bits 0x01..0x10)
   * @param newSigners replacement signer set (update_signers)
   * @param newThreshold replacement threshold (update_signers)
   * @return instruction payload including {@code proposalId}
   * @throws org.springframework.web.server.ResponseStatusException 400 bad fields, 403 not a
   *     signer, 404 unknown agent, 409 no governance or treasury too low
   */
  Map<String, Object> buildPropose(String walletPubkey, String kind, BigDecimal amountUsdc, String destinationWalletPubkey, String agentCode, String agentWalletPubkey, Integer permissions, List<String> newSigners, Integer newThreshold);

  /**
   * Verifies a {@code propose} signature and records the proposal plus the proposer's approval.
   *
   * @param walletPubkey signer wallet (base58)
   * @param kind withdraw, authorize_agent or update_signers
   * @param amountUsdc USDC amount
   * @param destinationWalletPubkey withdraw destination signer (defaults to walletPubkey)
   * @param agentCode ops agent code (for example NEXUS)
   * @param agentWalletPubkey agent signer wallet (base58)
   * @param permissions permission bitmask (bits 0x01..0x10)
   * @param newSigners replacement signer set (update_signers)
   * @param newThreshold replacement threshold (update_signers)
   * @param proposalId on-chain proposal id returned by the build step
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return stored proposal
   */
  GovernanceProposal confirmPropose(String walletPubkey, String kind, BigDecimal amountUsdc, String destinationWalletPubkey, String agentCode, String agentWalletPubkey, Integer permissions, List<String> newSigners, Integer newThreshold, Long proposalId, String txSignature);

  /**
   * Builds {@code approve_proposal} for a current signer who has not approved yet.
   *
   * @param proposalId governance_proposal id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   * @throws org.springframework.web.server.ResponseStatusException 403 not a signer, 404 unknown
   *     proposal, 409 executed / stale / already approved
   */
  Map<String, Object> buildApprove(Long proposalId,String walletPubkey);

  /**
   * Verifies an {@code approve_proposal} signature and records the approval.
   *
   * @param proposalId governance_proposal id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return stored approval
   */
  GovernanceApproval confirmApprove(Long proposalId,String walletPubkey, String txSignature);

  /**
   * Builds the {@code execute_*_proposal} that matches the proposal kind. Any wallet may execute.
   *
   * @param proposalId governance_proposal id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   * @throws org.springframework.web.server.ResponseStatusException 404 unknown proposal, 409
   *     executed / stale / below threshold / treasury too low
   */
  Map<String, Object> buildExecute(Long proposalId,String walletPubkey);

  /**
   * Verifies the execute signature and applies it: withdraw → treasury_withdrawal row, agent →
   * agent_authority row, signers → new signer set, threshold, and epoch.
   *
   * @param proposalId governance_proposal id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return executed proposal
   */
  GovernanceProposal confirmExecute(Long proposalId,String walletPubkey, String txSignature);
}
