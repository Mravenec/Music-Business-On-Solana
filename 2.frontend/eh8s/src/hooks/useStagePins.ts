import { useCallback, useEffect, useState } from "react";
import { fetchPins, type VenuePin } from "../services/stageMapService";
import { statusText } from "./statusText";

/** YYYY-MM for the current month plus an offset. */
export function monthKey(offset = 0): string {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

export type StagePinsState = {
  loading: boolean;
  error: string | null;
  pins: VenuePin[];
};

/**
 * Stage Map pins for one month (colors come from venues, open dates, and bookings).
 */
export function useStagePins(month: string): StagePinsState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pins, setPins] = useState<VenuePin[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setPins(await fetchPins(month));
      setError(null);
    } catch (err) {
      setError(statusText(err, {}, "Could not load the stage map."));
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { loading, error, pins };
}
