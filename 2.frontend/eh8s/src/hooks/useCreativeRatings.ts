import { useCallback, useEffect, useState } from "react";
import { fetchMembers, type BandMember } from "../services/bandService";
import {
  fetchCreativeRatings,
  rateBandmate,
  type CreativeRating,
} from "../services/sppInputsService";
import { statusText } from "./statusText";

export type CreativeRatingsState = {
  loading: boolean;
  error: string | null;
  /** Bandmates the signed-in musician has not rated yet for this rehearsal. */
  toRate: BandMember[];
  mine: CreativeRating[];
  rate: (rateeProfileId: number, score: number) => Promise<void>;
};

/**
 * Peer creative ratings for one rehearsal, from the signed-in musician's point of view.
 */
export function useCreativeRatings(
  bandId: number,
  sessionId: number,
  myProfileId: number | null
): CreativeRatingsState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [members, setMembers] = useState<BandMember[]>([]);
  const [ratings, setRatings] = useState<CreativeRating[]>([]);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(bandId) || !Number.isFinite(sessionId)) return;
    setLoading(true);
    try {
      const [m, r] = await Promise.all([fetchMembers(bandId), fetchCreativeRatings(sessionId)]);
      setMembers(m);
      setRatings(r);
      setError(null);
    } catch (err) {
      setError(statusText(err, {}, "Could not load this rehearsal."));
    } finally {
      setLoading(false);
    }
  }, [bandId, sessionId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const mine = ratings.filter((r) => r.raterProfileId === myProfileId);
  const rated = new Set(mine.map((r) => r.rateeProfileId));
  const toRate = members.filter(
    (m) => m.musicianProfileId !== myProfileId && !rated.has(m.musicianProfileId)
  );

  return {
    loading,
    error,
    toRate,
    mine,
    rate: async (rateeProfileId, score) => {
      try {
        await rateBandmate(sessionId, { rateeProfileId, score });
      } catch (err) {
        throw new Error(
          statusText(
            err,
            {
              400: "Pick a bandmate and a score from 1 to 5.",
              403: "Only members of this band can rate here.",
              409: "Check in to this rehearsal first, or you already rated this bandmate.",
            },
            "Could not save the rating."
          )
        );
      }
      await refresh();
    },
  };
}
