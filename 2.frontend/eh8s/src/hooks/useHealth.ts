import { useEffect, useState } from "react";
import { fetchHealth, type HealthPayload } from "../services/healthService";

export type HealthState = {
  loading: boolean;
  error: string | null;
  data: HealthPayload | null;
};

/**
 * Loads Health once on mount for the Health page.
 */
export function useHealth(): HealthState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<HealthPayload | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchHealth()
      .then((payload) => {
        if (!cancelled) {
          setData(payload);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          const message =
            err && typeof err === "object" && "message" in err
              ? String((err as { message: string }).message)
              : "Network Error";
          setError(message);
          setData(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { loading, error, data };
}
