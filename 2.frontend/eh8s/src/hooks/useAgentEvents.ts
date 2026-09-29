import { useCallback, useEffect, useState } from "react";
import { fetchAgentEvents, tickAgent, type AgentEvent } from "../services/agentEventService";

export function useAgentEvents() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [events, setEvents] = useState<AgentEvent[]>([]);

  const refresh = useCallback(async () => {
    const rows = await fetchAgentEvents();
    setEvents(rows);
    setError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    refresh()
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Network Error");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  return { loading, error, events, refresh, tickAgent };
}
