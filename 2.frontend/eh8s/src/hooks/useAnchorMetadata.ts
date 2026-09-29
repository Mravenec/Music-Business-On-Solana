import { useEffect, useState } from "react";
import { fetchAnchorMetadata, type AnchorMetadata } from "../services/anchorService";

/**
 * Loads Anchor DevNet scaffold metadata from the live API.
 */
export function useAnchorMetadata() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AnchorMetadata | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchAnchorMetadata()
      .then((row) => {
        if (cancelled) return;
        setData(row);
        setError(null);
      })
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
  }, []);

  return { loading, error, data };
}
