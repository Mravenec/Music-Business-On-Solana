import { useCallback, useEffect, useState } from "react";
import {
  fetchNexusEvaluation,
  fetchNexusEvaluations,
  fetchNexusStatus,
  requestScoreEnigma,
  type EnigmaEvaluation,
  type NexusEvaluationDetail,
  type NexusRequest,
  type NexusResult,
  type NexusStatus,
} from "../services/nexusService";
import { statusText } from "./statusText";

const ACCESS_TEXTS = {
  403: "Only the musician, a teacher or the owner can see these scores.",
  404: "That musician was not found.",
};

export type NexusState = {
  loading: boolean;
  error: string | null;
  status: NexusStatus | null;
  evaluations: EnigmaEvaluation[];
  request: (input: NexusRequest) => Promise<NexusResult>;
};

/**
 * NEXUS status (AI configured, rubric keys) plus the Score Enigma history of one musician.
 */
export function useNexus(musicianProfileId: number | null): NexusState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<NexusStatus | null>(null);
  const [evaluations, setEvaluations] = useState<EnigmaEvaluation[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await fetchNexusStatus());
      setEvaluations(musicianProfileId ? await fetchNexusEvaluations(musicianProfileId) : []);
      setError(null);
    } catch (err) {
      setError(statusText(err, ACCESS_TEXTS, "Could not load NEXUS."));
    } finally {
      setLoading(false);
    }
  }, [musicianProfileId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    status,
    evaluations,
    request: async (input) => {
      try {
        const out = await requestScoreEnigma(input);
        await refresh();
        return out;
      } catch (err) {
        throw new Error(
          statusText(
            err,
            {
              ...ACCESS_TEXTS,
              400: "Rate every rubric item from 0 to 10 and use an http(s) recording link.",
              502: "NEXUS answered without a score. Try again.",
              503: "NEXUS is offline: the server has no Anthropic API key.",
            },
            "Could not request the Score Enigma."
          )
        );
      }
    },
  };
}

export type NexusEvaluationState = {
  loading: boolean;
  error: string | null;
  detail: NexusEvaluationDetail | null;
};

/**
 * One stored Score Enigma with the musician's current level.
 */
export function useNexusEvaluation(id: number): NexusEvaluationState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<NexusEvaluationDetail | null>(null);

  useEffect(() => {
    if (!Number.isFinite(id)) return;
    let live = true;
    setLoading(true);
    fetchNexusEvaluation(id)
      .then((d) => {
        if (!live) return;
        setDetail(d);
        setError(null);
      })
      .catch((err) => {
        if (live) setError(statusText(err, { ...ACCESS_TEXTS, 404: "That evaluation was not found." }, "Could not load the evaluation."));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [id]);

  return { loading, error, detail };
}
