import { useCallback, useEffect, useState } from "react";
import {
  endSet,
  fetchSetCheckins,
  startSet,
  type ConcertSetCheckin,
} from "../services/sppInputsService";
import { statusText } from "./statusText";

export type SetCheckinState = {
  loading: boolean;
  error: string | null;
  /** The signed-in musician's set row for this concert, if started. */
  mine: ConcertSetCheckin | null;
  start: () => Promise<void>;
  end: () => Promise<void>;
};

const TEXTS = {
  403: "Only members of this concert's band can check in a set.",
  409: "Your set is not in a state for this step.",
};

/**
 * Set start / end check-in for one concert (minutes played feed the concert SPP variable).
 */
export function useSetCheckin(concertId: number, myProfileId: number | null): SetCheckinState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<ConcertSetCheckin[]>([]);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(concertId)) return;
    setLoading(true);
    try {
      setRows(await fetchSetCheckins(concertId));
      setError(null);
    } catch (err) {
      setError(statusText(err, {}, "Could not load this concert."));
    } finally {
      setLoading(false);
    }
  }, [concertId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function step(call: () => Promise<unknown>, fallback: string) {
    try {
      await call();
    } catch (err) {
      throw new Error(statusText(err, TEXTS, fallback));
    }
    await refresh();
  }

  return {
    loading,
    error,
    mine: rows.find((r) => r.musicianProfileId === myProfileId) ?? null,
    start: () => step(() => startSet(concertId), "Could not start your set."),
    end: () => step(() => endSet(concertId), "Could not end your set."),
  };
}
