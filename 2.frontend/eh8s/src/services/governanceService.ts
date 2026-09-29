import { apiClient as client } from "./http";
import type { BuiltIx } from "./sendBuiltIx";

export type ProposalKind = "withdraw" | "authorize_agent" | "update_signers";

/** JOOQ Governance POJO keys. */
export type Governance = {
  id: number;
  programId: string;
  governancePda: string;
  threshold: number;
  epoch: number;
  initializedByWallet: string;
  initTxSignature: string;
  createdAt: string;
  updatedAt: string;
};

/** JOOQ GovernanceProposal POJO keys. */
export type GovernanceProposal = {
  id: number;
  governanceId: number;
  onchainProposalId: number;
  proposalPda: string;
  epoch: number;
  kind: ProposalKind;
  proposerWallet: string;
  amountUsdc: number | null;
  targetPubkey: string | null;
  permissions: number | null;
  agentCode: string | null;
  newSignersCsv: string | null;
  newThreshold: number | null;
  status: "open" | "executed" | "stale";
  proposeTxSignature: string;
  executeTxSignature: string | null;
  executedByWallet: string | null;
  createdAt: string;
  executedAt: string | null;
};

/** JOOQ GovernanceApproval POJO keys. */
export type GovernanceApproval = {
  id: number;
  proposalId: number;
  signerWallet: string;
  txSignature: string;
  createdAt: string;
};

export type ProposalRow = {
  proposal: GovernanceProposal;
  approvals: string[];
  approvalCount: number;
  approvedByMe: boolean;
  executable: boolean;
};

export type GovernanceView = {
  programId: string;
  ownerWalletPubkey: string;
  governancePda: string;
  initialized: boolean;
  signerRole: "owner" | "signer";
  maxSigners: number;
  governance: Governance | null;
  signers: string[];
  proposals: ProposalRow[];
};

export type GovernanceBuild = BuiltIx & {
  instruction: string;
  governancePda: string;
  proposalId?: number;
  proposalPda?: string;
  kind?: ProposalKind;
  note: string;
};

/** Fields a proposal carries; unused fields for a kind are omitted. */
export type ProposalInput = {
  kind: ProposalKind;
  amountUsdc?: string;
  destinationWalletPubkey?: string;
  agentCode?: string;
  agentWalletPubkey?: string;
  permissions?: number;
  newSigners?: string[];
  newThreshold?: number;
};

function requireSignature(txSignature: string) {
  if (!txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to record without a DevNet signature");
  }
}

/** Signers, threshold, and proposals (owner or current signer; 403 otherwise). */
export async function fetchGovernance(walletPubkey: string): Promise<GovernanceView> {
  const { data } = await client.get<GovernanceView>("/api/governance", { params: { walletPubkey } });
  return data;
}

/** Builds init_governance for the owner wallet. */
export async function buildInitGovernance(body: {
  walletPubkey: string;
  signers: string[];
  threshold: number;
}): Promise<GovernanceBuild> {
  const { data } = await client.post<GovernanceBuild>("/api/governance/init/build", body);
  return data;
}

/** Records a DevNet-confirmed init_governance. */
export async function confirmInitGovernance(body: {
  walletPubkey: string;
  signers: string[];
  threshold: number;
  txSignature: string;
}): Promise<Governance> {
  requireSignature(body.txSignature);
  const { data } = await client.post<Governance>("/api/governance/init/confirm", body);
  return data;
}

/** Builds propose for a signer; the response carries the on-chain proposalId. */
export async function buildPropose(
  body: ProposalInput & { walletPubkey: string },
): Promise<GovernanceBuild> {
  const { data } = await client.post<GovernanceBuild>("/api/governance/proposals/build", body);
  return data;
}

/** Records a DevNet-confirmed proposal (the proposer's approval is stored with it). */
export async function confirmPropose(
  body: ProposalInput & { walletPubkey: string; proposalId: number; txSignature: string },
): Promise<GovernanceProposal> {
  requireSignature(body.txSignature);
  const { data } = await client.post<GovernanceProposal>("/api/governance/proposals/confirm", body);
  return data;
}

/** Builds approve_proposal for a signer who has not approved yet. */
export async function buildApprove(id: number, body: { walletPubkey: string }): Promise<GovernanceBuild> {
  const { data } = await client.post<GovernanceBuild>(`/api/governance/proposals/${id}/approve/build`, body);
  return data;
}

/** Records a DevNet-confirmed approval. */
export async function confirmApprove(
  id: number,
  body: { walletPubkey: string; txSignature: string },
): Promise<GovernanceApproval> {
  requireSignature(body.txSignature);
  const { data } = await client.post<GovernanceApproval>(
    `/api/governance/proposals/${id}/approve/confirm`,
    body,
  );
  return data;
}

/** Builds the execute instruction that matches the proposal kind (409 below threshold). */
export async function buildExecute(id: number, body: { walletPubkey: string }): Promise<GovernanceBuild> {
  const { data } = await client.post<GovernanceBuild>(`/api/governance/proposals/${id}/execute/build`, body);
  return data;
}

/** Records a DevNet-confirmed execution and applies it (withdrawal, agent grant, or signer set). */
export async function confirmExecute(
  id: number,
  body: { walletPubkey: string; txSignature: string },
): Promise<GovernanceProposal> {
  requireSignature(body.txSignature);
  const { data } = await client.post<GovernanceProposal>(
    `/api/governance/proposals/${id}/execute/confirm`,
    body,
  );
  return data;
}
