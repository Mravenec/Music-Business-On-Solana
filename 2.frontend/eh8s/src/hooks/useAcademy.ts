import { useCallback, useEffect, useState } from "react";
import {
  createEvaluation,
  createMusician,
  createSubscription,
  fetchEvaluations,
  fetchInstruments,
  fetchLevels,
  fetchMusicians,
  fetchPlans,
  fetchSubscriptions,
  type AcademyPlan,
  type AcademySubscription,
  type EnigmaEvaluation,
  type EnigmaLevel,
  type Instrument,
  type MusicianProfile,
} from "../services/academyService";

export type AcademyState = {
  loading: boolean;
  error: string | null;
  instruments: Instrument[];
  levels: EnigmaLevel[];
  plans: AcademyPlan[];
  musicians: MusicianProfile[];
  subscriptions: AcademySubscription[];
  evaluations: EnigmaEvaluation[];
  refresh: () => Promise<void>;
  enrollMusician: (input: {
    accountId: number;
    instrumentId: number;
    countryCode: string;
  }) => Promise<void>;
  subscribe: (input: {
    musicianProfileId: number;
    academyPlanId: number;
    months?: number;
  }) => Promise<AcademySubscription>;
  evaluate: (input: {
    musicianProfileId: number;
    score: number;
    notes?: string;
  }) => Promise<void>;
};

/**
 * Live academy data and mutation helpers (MariaDB via Spring).
 */
export function useAcademy(): AcademyState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [levels, setLevels] = useState<EnigmaLevel[]>([]);
  const [plans, setPlans] = useState<AcademyPlan[]>([]);
  const [musicians, setMusicians] = useState<MusicianProfile[]>([]);
  const [subscriptions, setSubscriptions] = useState<AcademySubscription[]>([]);
  const [evaluations, setEvaluations] = useState<EnigmaEvaluation[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [i, l, p, m, s, e] = await Promise.all([
        fetchInstruments(),
        fetchLevels(),
        fetchPlans(),
        fetchMusicians(),
        fetchSubscriptions(),
        fetchEvaluations(),
      ]);
      setInstruments(i);
      setLevels(l);
      setPlans(p);
      setMusicians(m);
      setSubscriptions(s);
      setEvaluations(e);
      setError(null);
    } catch (err: unknown) {
      const message =
        err && typeof err === "object" && "message" in err
          ? String((err as { message: string }).message)
          : "Network Error";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    instruments,
    levels,
    plans,
    musicians,
    subscriptions,
    evaluations,
    refresh,
    enrollMusician: async (input) => {
      await createMusician(input);
      await refresh();
    },
    subscribe: async (input) => {
      const created = await createSubscription(input);
      await refresh();
      return created;
    },
    evaluate: async (input) => {
      await createEvaluation(input);
      await refresh();
    },
  };
}
