import { apiClient as client } from "./http";

export type AgentEvent = {
  id: number;
  opsAgentId: number;
  eventType: string;
  semaphore: string;
  summary: string;
  detail: string;
  ownerDecisionId?: number | null;
  createdAt?: string | null;
};

type OpsAgent = {
  id: number;
  code: string;
};

export async function fetchAgentEvents(): Promise<AgentEvent[]> {
  const { data } = await client.get<AgentEvent[]>("/api/agent-events");
  return data;
}

/**
 * Resolves agent by code, then POSTs /api/agents/{id}/tick.
 */
export async function tickAgent(body: {
  agentCode?: string;
  semaphore?: string;
  eventType: string;
  summary: string;
  detail?: string;
}): Promise<AgentEvent> {
  const code = (body.agentCode || "").trim();
  if (!code) {
    throw new Error("agentCode is required");
  }
  const { data: agents } = await client.get<OpsAgent[]>("/api/agents");
  const agent = agents.find((a) => a.code.toUpperCase() === code.toUpperCase());
  if (!agent) {
    throw new Error(`Unknown agent code: ${code}`);
  }
  const { data } = await client.post<AgentEvent>(`/api/agents/${agent.id}/tick`, {
    eventType: body.eventType,
    summary: body.summary,
    detail: body.detail,
    semaphore: body.semaphore,
  });
  return data;
}
