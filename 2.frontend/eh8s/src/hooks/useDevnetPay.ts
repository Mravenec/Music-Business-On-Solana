import { useCallback, useEffect, useState } from "react";
import {
  fetchMarkers,
  type DevnetPayMarker,
} from "../services/devnetPayService";

/**
 * Loads DevNet pay markers and exposes recording helpers.
 */
export function useDevnetPay() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [markers, setMarkers] = useState<DevnetPayMarker[]>([]);

  const refresh = useCallback(async () => {
    const rows = await fetchMarkers();
    setMarkers(rows);
    setError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    refresh()
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
  }, [refresh]);

  return {
    loading,
    error,
    markers,
    refresh,
  };
}
