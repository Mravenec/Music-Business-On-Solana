import { useEffect, useState } from "react";
import { fetchChainConfig, type ChainConfig } from "../services/chainConfigService";
import { getAccessToken } from "../services/http";
import { useSession } from "./useSession";

export type ChainConfigState = {
  loading: boolean;
  error: string | null;
  data: ChainConfig | null;
};

/**
 * Loads BagsCreatorFund-style Solana client settings from the API after JWT is stored.
 */
export function useChainConfig(): ChainConfigState {
  const { session } = useSession();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<ChainConfig | null>(null);
  const ready = Boolean(session?.accessToken || getAccessToken());

  useEffect(() => {
    if (!ready) {
      setLoading(false);
      setData(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchChainConfig()
      .then((row) => {
        if (cancelled) return;
        setData(row);
        setError(null);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message =
          err && typeof err === "object" && "message" in err
            ? String((err as { message: string }).message)
            : "Network Error";
        setError(message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [ready]);

  return { loading, error, data };
}
