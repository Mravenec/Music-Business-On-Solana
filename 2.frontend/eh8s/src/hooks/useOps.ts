import { useCallback, useEffect, useState } from "react";
import {
  approveDecision,
  fetchAgents,
  fetchDecisions,
  rejectDecision,
  setAgentSemaphore,
  type OpsAgent,
  type OwnerDecision,
} from "../services/opsService";
import { statusText } from "./statusText";

const DECIDE_TEXTS: Partial<Record<number, string>> = {
  403: "Only the owner wallet can answer agent decisions.",
  404: "That decision no longer exists.",
  409: "Already answered (possibly in Slack). The list now shows the current answer.",
};

export type OpsState = {
  loading: boolean;
  error: string | null;
  agents: OpsAgent[];
  decisions: OwnerDecision[];
  refresh: () => Promise<void>;
  approve: (id: number) => Promise<void>;
  reject: (id: number, reason: string) => Promise<void>;
  setSemaphore: (id: number, semaphore: string) => Promise<void>;
};

/**
 * Live owner console data plus approve / reject / semaphore mutations.
 */
export function useOps(): OpsState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [agents, setAgents] = useState<OpsAgent[]>([]);
  const [decisions, setDecisions] = useState<OwnerDecision[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [a, d] = await Promise.all([fetchAgents(), fetchDecisions()]);
      setAgents(a);
      setDecisions(d);
      setError(null);
    } catch (err: unknown) {
      setError(statusText(err, DECIDE_TEXTS, "Could not load agents and decisions."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    agents,
    decisions,
    refresh,
    approve: async (id) => {
      try {
        await approveDecision(id);
      } catch (err) {
        await refresh();
        throw new Error(statusText(err, DECIDE_TEXTS, "Could not approve"));
      }
      await refresh();
    },
    reject: async (id, reason) => {
      try {
        await rejectDecision(id, reason);
      } catch (err) {
        await refresh();
        throw new Error(statusText(err, DECIDE_TEXTS, "Could not reject"));
      }
      await refresh();
    },
    setSemaphore: async (id, semaphore) => {
      await setAgentSemaphore(id, semaphore);
      await refresh();
    },
  };
}
