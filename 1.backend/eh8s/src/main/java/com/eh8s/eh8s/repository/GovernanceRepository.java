package com.eh8s.eh8s.repository;

import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.CHAIN_CONFIG;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.GOVERNANCE;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.GOVERNANCE_APPROVAL;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.GOVERNANCE_PROPOSAL;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.GOVERNANCE_SIGNER;
import static com.eh8s.eh8s.database.jooq.eh8s_onchain.Tables.TREASURY_WITHDRAWAL;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.Governance;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceApproval;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceProposal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.GovernanceSigner;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.GovernanceApprovalRecord;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.GovernanceProposalRecord;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.GovernanceRecord;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.records.TreasuryWithdrawalRecord;
import com.eh8s.eh8s.repository.interfaces.IGovernanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JOOQ persistence for the owner multisig (governance, signers, proposals, approvals).
 */
@Repository
public class GovernanceRepository implements IGovernanceRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public GovernanceRepository(DSLContext dsl) {
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
  public Optional<Governance> findGovernance(String programId) {
    return dsl.selectFrom(GOVERNANCE)
        .where(GOVERNANCE.PROGRAM_ID.eq(programId))
        .orderBy(GOVERNANCE.ID.desc())
        .limit(1)
        .fetchOptionalInto(Governance.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<GovernanceSigner> findSigners(Long governanceId) {
    return dsl.selectFrom(GOVERNANCE_SIGNER)
        .where(GOVERNANCE_SIGNER.GOVERNANCE_ID.eq(governanceId))
        .orderBy(GOVERNANCE_SIGNER.POSITION)
        .fetchInto(GovernanceSigner.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public Governance insertGovernance(Governance governance, List<String> signers) {
    GovernanceRecord rec = dsl.newRecord(GOVERNANCE, governance);
    rec.changed(GOVERNANCE.ID, false);
    rec.store();
    insertSigners(rec.getId(), signers);
    return rec.into(Governance.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<GovernanceProposal> findProposals(Long governanceId) {
    return dsl.selectFrom(GOVERNANCE_PROPOSAL)
        .where(GOVERNANCE_PROPOSAL.GOVERNANCE_ID.eq(governanceId))
        .orderBy(GOVERNANCE_PROPOSAL.ONCHAIN_PROPOSAL_ID.desc())
        .fetchInto(GovernanceProposal.class);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GovernanceProposal> findProposal(Long proposalId) {
    return dsl.selectFrom(GOVERNANCE_PROPOSAL)
        .where(GOVERNANCE_PROPOSAL.ID.eq(proposalId))
        .fetchOptionalInto(GovernanceProposal.class);
  }

  /** {@inheritDoc} */
  @Override
  public List<GovernanceApproval> findApprovals(Long proposalId) {
    return dsl.selectFrom(GOVERNANCE_APPROVAL)
        .where(GOVERNANCE_APPROVAL.PROPOSAL_ID.eq(proposalId))
        .orderBy(GOVERNANCE_APPROVAL.ID)
        .fetchInto(GovernanceApproval.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public GovernanceProposal insertProposal(GovernanceProposal proposal) {
    GovernanceProposalRecord rec = dsl.newRecord(GOVERNANCE_PROPOSAL, proposal);
    rec.changed(GOVERNANCE_PROPOSAL.ID, false);
    rec.store();
    GovernanceApproval approval = new GovernanceApproval();
    approval.setProposalId(rec.getId());
    approval.setSignerWallet(proposal.getProposerWallet());
    approval.setTxSignature(proposal.getProposeTxSignature());
    approval.setCreatedAt(proposal.getCreatedAt());
    insertApproval(approval);
    return rec.into(GovernanceProposal.class);
  }

  /** {@inheritDoc} */
  @Override
  public GovernanceApproval insertApproval(GovernanceApproval approval) {
    GovernanceApprovalRecord rec = dsl.newRecord(GOVERNANCE_APPROVAL, approval);
    rec.changed(GOVERNANCE_APPROVAL.ID, false);
    rec.store();
    return rec.into(GovernanceApproval.class);
  }

  /** {@inheritDoc} */
  @Override
  public void markExecuted(GovernanceProposal proposal) {
    dsl.update(GOVERNANCE_PROPOSAL)
        .set(GOVERNANCE_PROPOSAL.STATUS, "executed")
        .set(GOVERNANCE_PROPOSAL.EXECUTE_TX_SIGNATURE, proposal.getExecuteTxSignature())
        .set(GOVERNANCE_PROPOSAL.EXECUTED_BY_WALLET, proposal.getExecutedByWallet())
        .set(GOVERNANCE_PROPOSAL.EXECUTED_AT, proposal.getExecutedAt())
        .where(GOVERNANCE_PROPOSAL.ID.eq(proposal.getId()))
        .execute();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public TreasuryWithdrawal recordWithdrawExecution(
      GovernanceProposal proposal, TreasuryWithdrawal withdrawal) {
    markExecuted(proposal);
    TreasuryWithdrawalRecord rec = dsl.newRecord(TREASURY_WITHDRAWAL, withdrawal);
    rec.changed(TREASURY_WITHDRAWAL.ID, false);
    rec.store();
    return rec.into(TreasuryWithdrawal.class);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public Governance recordSignersExecution(
      GovernanceProposal proposal, Governance governance, List<String> newSigners, int newThreshold) {
    markExecuted(proposal);
    int epoch = governance.getEpoch() + 1;
    LocalDateTime now = proposal.getExecutedAt();
    dsl.update(GOVERNANCE)
        .set(GOVERNANCE.THRESHOLD, (byte) newThreshold)
        .set(GOVERNANCE.EPOCH, epoch)
        .set(GOVERNANCE.UPDATED_AT, now)
        .where(GOVERNANCE.ID.eq(governance.getId()))
        .execute();
    dsl.deleteFrom(GOVERNANCE_SIGNER).where(GOVERNANCE_SIGNER.GOVERNANCE_ID.eq(governance.getId())).execute();
    insertSigners(governance.getId(), newSigners);
    dsl.update(GOVERNANCE_PROPOSAL)
        .set(GOVERNANCE_PROPOSAL.STATUS, "stale")
        .where(GOVERNANCE_PROPOSAL.GOVERNANCE_ID.eq(governance.getId()))
        .and(GOVERNANCE_PROPOSAL.STATUS.eq("open"))
        .and(GOVERNANCE_PROPOSAL.EPOCH.lt(epoch))
        .execute();
    governance.setThreshold((byte) newThreshold);
    governance.setEpoch(epoch);
    governance.setUpdatedAt(now);
    return governance;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRecorded(String txSignature) {
    return dsl.fetchExists(GOVERNANCE, GOVERNANCE.INIT_TX_SIGNATURE.eq(txSignature))
        || dsl.fetchExists(
            GOVERNANCE_PROPOSAL,
            GOVERNANCE_PROPOSAL.PROPOSE_TX_SIGNATURE.eq(txSignature)
                .or(GOVERNANCE_PROPOSAL.EXECUTE_TX_SIGNATURE.eq(txSignature)))
        || dsl.fetchExists(GOVERNANCE_APPROVAL, GOVERNANCE_APPROVAL.TX_SIGNATURE.eq(txSignature));
  }

  private void insertSigners(Long governanceId, List<String> signers) {
    for (int i = 0; i < signers.size(); i++) {
      dsl.insertInto(GOVERNANCE_SIGNER)
          .set(GOVERNANCE_SIGNER.GOVERNANCE_ID, governanceId)
          .set(GOVERNANCE_SIGNER.WALLET_PUBKEY, signers.get(i))
          .set(GOVERNANCE_SIGNER.POSITION, (byte) i)
          .execute();
    }
  }
}
