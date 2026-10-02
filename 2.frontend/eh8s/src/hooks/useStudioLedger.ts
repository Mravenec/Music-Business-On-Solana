import { useEffect, useState } from "react";
import {
  fetchInstructorShares,
  fetchStudioClaims,
  type InstructorShare,
  type StudioClaim,
} from "../services/studioLedgerService";

/**
 * Confirmed studio credits for the signed-in wallet in one calendar month.
 */
export function useStudioLedger(year: number, month: number) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [claims, setClaims] = useState<StudioClaim[]>([]);
  const [shares, setShares] = useState<InstructorShare[]>([]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([fetchStudioClaims(year, month), fetchInstructorShares(year, month)])
      .then(([claimRows, shareRows]) => {
        if (cancelled) return;
        setClaims(claimRows);
        setShares(shareRows);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setClaims([]);
        setShares([]);
        setError("Could not load studio earnings");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [year, month]);

  return { loading, error, claims, shares };
}
