import { apiClient as client } from "./http";

export type OpsAgent = {
  id: number;
  code: string;
  name: string;
  roleSummary: string;
  semaphore: string;
  taskStatus?: string;
  walletPubkey: string | null;
};

export type OwnerDecision = {
  id: number;
  title: string;
  semaphore: string;
  payload: string;
  status: string;
  rejectReason?: string | null;
  resolvedAt?: string | null;
  /** Where the owner answered: `web` or `slack` (null while pending). */
  answeredVia?: string | null;
  /** Slack user (`name (id)`) for Slack answers; null for web answers. */
  answeredBy?: string | null;
};

export async function fetchAgents(): Promise<OpsAgent[]> {
  const { data } = await client.get<OpsAgent[]>("/api/agents");
  return data;
}

export async function fetchDecisions(): Promise<OwnerDecision[]> {
  const { data } = await client.get<OwnerDecision[]>("/api/owner-decisions");
  return data;
}

export async function approveDecision(id: number): Promise<OwnerDecision> {
  const { data } = await client.post<OwnerDecision>(`/api/owner-decisions/${id}/approve`);
  return data;
}

export async function rejectDecision(
  id: number,
  rejectReason: string,
): Promise<OwnerDecision> {
  const { data } = await client.post<OwnerDecision>(`/api/owner-decisions/${id}/reject`, {
    rejectReason,
  });
  return data;
}

export async function setAgentSemaphore(
  id: number,
  semaphore: string,
): Promise<OpsAgent> {
  const { data } = await client.post<OpsAgent>(`/api/agents/${id}/semaphore`, { semaphore });
  return data;
}
