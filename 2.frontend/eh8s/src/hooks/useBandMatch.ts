import { useCallback, useEffect, useState } from "react";
import {
  fetchMatchSuggestions,
  runHarmony,
  type MatchSuggestions,
} from "../services/harmonyService";
import { statusText } from "./statusText";

const ACCESS_TEXTS = {
  403: "Only band members or the owner can use HARMONY for this band.",
  404: "That band was not found.",
};

export type BandMatchState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  data: MatchSuggestions | null;
  run: (withAi: boolean) => Promise<MatchSuggestions>;
};

/**
 * HARMONY suggestions for one band, and a re-run that stores a fresh ranking.
 */
export function useBandMatch(bandId: number): BandMatchState {
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<MatchSuggestions | null>(null);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(bandId)) return;
    setLoading(true);
    try {
      setData(await fetchMatchSuggestions(bandId));
      setError(null);
    } catch (err) {
      setError(statusText(err, ACCESS_TEXTS, "Could not load suggestions."));
    } finally {
      setLoading(false);
    }
  }, [bandId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    busy,
    error,
    data,
    run: async (withAi) => {
      setBusy(true);
      try {
        const out = await runHarmony(bandId, withAi);
        setData(out);
        return out;
      } catch (err) {
        throw new Error(statusText(err, ACCESS_TEXTS, "Could not run HARMONY."));
      } finally {
        setBusy(false);
      }
    },
  };
}
