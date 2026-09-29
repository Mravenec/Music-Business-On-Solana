package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import java.util.Map;

/**
 * HTTP surface for the owner multisig (program v0.8.0). Every route needs a JWT Bearer; build
 * routes return an unsigned instruction, confirm routes verify the DevNet signature.
 */
public interface IGovernanceController {

  /**
   * {@code GET /api/governance?walletPubkey=} — signer set, threshold, proposals (owner or signer).
   *
   * @param walletPubkey connected wallet
   * @return governance view
   */
  Map<String, Object> overview(String walletPubkey);

  /**
   * {@code POST /api/governance/init/build} — owner builds {@code init_governance}.
   *
   * @param body walletPubkey, signers, threshold
   * @return instruction payload
   */
  Map<String, Object> buildInit(Map<String, Object> body);

  /**
   * {@code POST /api/governance/init/confirm} — records a verified {@code init_governance}.
   *
   * @param body build fields plus txSignature
   * @return stored governance
   */
  Governance confirmInit(Map<String, Object> body);

  /**
   * {@code POST /api/governance/proposals/build} — a signer builds {@code propose}.
   *
   * @param body walletPubkey, kind, kind fields
   * @return instruction payload with proposalId
   */
  Map<String, Object> buildPropose(Map<String, Object> body);

  /**
   * {@code POST /api/governance/proposals/confirm} — records a verified proposal.
   *
   * @param body build fields plus proposalId and txSignature
   * @return stored proposal
   */
  GovernanceProposal confirmPropose(Map<String, Object> body);

  /**
   * {@code POST /api/governance/proposals/{id}/approve/build} — a signer builds {@code
   * approve_proposal}.
   *
   * @param id governance_proposal id
   * @param body walletPubkey
   * @return instruction payload
   */
  Map<String, Object> buildApprove(Long id, Map<String, Object> body);

  /**
   * {@code POST /api/governance/proposals/{id}/approve/confirm} — records a verified approval.
   *
   * @param id governance_proposal id
   * @param body walletPubkey, txSignature
   * @return stored approval
   */
  GovernanceApproval confirmApprove(Long id, Map<String, Object> body);

  /**
   * {@code POST /api/governance/proposals/{id}/execute/build} — builds the matching execute.
   *
   * @param id governance_proposal id
   * @param body walletPubkey
   * @return instruction payload
   */
  Map<String, Object> buildExecute(Long id, Map<String, Object> body);

  /**
   * {@code POST /api/governance/proposals/{id}/execute/confirm} — verifies and applies it.
   *
   * @param id governance_proposal id
   * @param body walletPubkey, txSignature
   * @return executed proposal
   */
  GovernanceProposal confirmExecute(Long id, Map<String, Object> body);
}
