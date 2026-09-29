import { useCallback, useEffect, useState } from "react";
import { fetchAcademyOnchainStatus } from "../services/academyOnchainService";
import { fetchGeoOnchainStatus } from "../services/royaltyGeoOnchainService";

export type SubscriptionExpiry = {
  loading: boolean;
  exists: boolean;
  active: boolean;
  expiresAt: string | null;
  refresh: () => Promise<void>;
};

/**
 * Live expires_at read from the subscription PDA on DevNet (academy or geo zone).
 * Skips the call until a paid subscription id exists.
 */
export function useSubscriptionExpiry(
  kind: "academy" | "geo",
  subscriptionId: number | null | undefined,
): SubscriptionExpiry {
  const [loading, setLoading] = useState(false);
  const [exists, setExists] = useState(false);
  const [active, setActive] = useState(false);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!subscriptionId) return;
    setLoading(true);
    try {
      const row =
        kind === "academy"
          ? await fetchAcademyOnchainStatus(subscriptionId)
          : await fetchGeoOnchainStatus(subscriptionId);
      setExists(row.exists);
      setActive(row.active);
      setExpiresAt(row.expiresAt);
    } catch {
      setExists(false);
      setActive(false);
      setExpiresAt(null);
    } finally {
      setLoading(false);
    }
  }, [kind, subscriptionId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { loading, exists, active, expiresAt, refresh };
}
