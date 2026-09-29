import { apiClient as client } from "./http";
import type { BuiltIx } from "./sendBuiltIx";

/** One on-chain permission bit the owner can grant (program v0.6.0). */
export type PermissionBit = { mask: number; name: string; power: string };

/** JOOQ OpsAgent POJO keys used by the roster. */
export type OpsAgent = {
  id: number;
  code: string;
  name: string;
  roleSummary?: string | null;
  semaphore?: string | null;
  taskStatus?: string | null;
  onChainPermission?: number | null;
  permissionBits?: number | null;
  walletPubkey?: string | null;
};

/** JOOQ AgentAuthority POJO keys (one confirmed authorize_agent). */
export type AgentAuthority = {
  id: number;
  opsAgentId: number;
  agentWalletPubkey: string;
  permissions: number;
  agentAuthorityPda: string;
  ownerWalletPubkey: string;
  txSignature: string;
  createdAt: string;
};

export type AgentRoster = {
  ownerWalletPubkey: string;
  programId: string;
  permissionBits: PermissionBit[];
  agents: OpsAgent[];
  authorizations: AgentAuthority[];
  /** True once init_governance is recorded: agent powers go through proposals. */
  governanceActive?: boolean;
};

export type AuthorizeAgentBuild = BuiltIx & {
  instruction: string;
  agentCode: string;
  agentWalletPubkey: string;
  permissions: number;
  agentAuthorityPda: string;
  note: string;
};

/** One musician the level console can move (wallet required for the on-chain profile). */
export type LevelTarget = {
  musicianProfileId: number;
  displayName: string;
  walletPubkey: string | null;
  levelNumber: number | null;
  levelName: string | null;
  countryCode: string | null;
};

export type LevelConsole = {
  signerWalletPubkey: string;
  signerRole: "owner" | "pedagogical_agent";
  maxLevel: number;
  musicians: LevelTarget[];
};

export type UpdateLevelBuild = BuiltIx & {
  instruction: string;
  musicianProfileId: number;
  currentLevel: number | null;
  newLevel: number;
  musicianProfilePda: string;
  agentAuthorityAccount: string;
  note: string;
};

/** JOOQ MusicianLevelChange POJO keys. */
export type MusicianLevelChange = {
  id: number;
  musicianProfileId: number;
  agentWalletPubkey: string;
  oldLevel: number;
  newLevel: number;
  txSignature: string;
  createdAt: string;
};

function requireSignature(txSignature: string) {
  if (!txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to record without a DevNet signature");
  }
}

/** Agents, permission bits, and confirmed on-chain authorizations (owner wallet only). */
export async function fetchAgentRoster(walletPubkey: string): Promise<AgentRoster> {
  const { data } = await client.get<AgentRoster>("/api/owner/agents", { params: { walletPubkey } });
  return data;
}

/** Builds authorize_agent for the owner wallet; permissions 0 revokes every power. */
export async function buildAuthorizeAgent(body: {
  walletPubkey: string;
  agentCode: string;
  agentWalletPubkey: string;
  permissions: number;
}): Promise<AuthorizeAgentBuild> {
  const { data } = await client.post<AuthorizeAgentBuild>("/api/owner/agents/authorize/build", body);
  return data;
}

/** Records a DevNet-confirmed authorize_agent (backend verifies the signature over RPC). */
export async function confirmAuthorizeAgent(body: {
  walletPubkey: string;
  agentCode: string;
  agentWalletPubkey: string;
  permissions: number;
  txSignature: string;
}): Promise<AgentAuthority> {
  requireSignature(body.txSignature);
  const { data } = await client.post<AgentAuthority>("/api/owner/agents/authorize/confirm", body);
  return data;
}

/** Musicians the signer may move (owner or a live 0x01 pedagogical agent; 403 otherwise). */
export async function fetchLevelConsole(walletPubkey: string): Promise<LevelConsole> {
  const { data } = await client.get<LevelConsole>("/api/agents/level/musicians", {
    params: { walletPubkey },
  });
  return data;
}

/** Builds update_musician_level for the connected owner / pedagogical agent. */
export async function buildUpdateLevel(body: {
  walletPubkey: string;
  musicianProfileId: number;
  newLevel: number;
}): Promise<UpdateLevelBuild> {
  const { data } = await client.post<UpdateLevelBuild>("/api/agents/level/build", body);
  return data;
}

/** Records a DevNet-confirmed update_musician_level and moves the off-chain level. */
export async function confirmUpdateLevel(body: {
  walletPubkey: string;
  musicianProfileId: number;
  newLevel: number;
  txSignature: string;
}): Promise<MusicianLevelChange> {
  requireSignature(body.txSignature);
  const { data } = await client.post<MusicianLevelChange>("/api/agents/level/confirm", body);
  return data;
}
