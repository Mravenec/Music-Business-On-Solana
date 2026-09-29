import { useCallback, useEffect, useState } from "react";
import {
  fetchTourPlan,
  fetchTourRoutes,
  generateTourRoute,
  type TourPlanDetail,
  type TourRequest,
  type TourRoutes,
} from "../services/atlasService";
import { statusText } from "./statusText";

const ACCESS_TEXTS = {
  403: "Only band members or the owner can plan tours for this band.",
  404: "That band was not found.",
};

export type TourRoutesState = {
  loading: boolean;
  error: string | null;
  data: TourRoutes | null;
  generate: (input: TourRequest) => Promise<TourPlanDetail>;
};

/**
 * ATLAS tour plans and active zones for one band, plus route generation.
 */
export function useTourRoutes(bandId: number): TourRoutesState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<TourRoutes | null>(null);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(bandId)) return;
    setLoading(true);
    try {
      setData(await fetchTourRoutes(bandId));
      setError(null);
    } catch (err) {
      setError(statusText(err, ACCESS_TEXTS, "Could not load tour routes."));
    } finally {
      setLoading(false);
    }
  }, [bandId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    data,
    generate: async (input) => {
      try {
        return await generateTourRoute(bandId, input);
      } catch (err) {
        throw new Error(
          statusText(
            err,
            {
              ...ACCESS_TEXTS,
              400: "Pick a window that starts today or later, spans at most 92 days, and 1 to 10 stops.",
              409: "No approved venue is reachable: the band needs an active zone subscription with venues in it.",
            },
            "Could not generate the route."
          )
        );
      }
    },
  };
}

export type TourPlanState = { loading: boolean; error: string | null; detail: TourPlanDetail | null };

/**
 * One stored tour plan with its ordered stops.
 */
export function useTourPlan(planId: number): TourPlanState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<TourPlanDetail | null>(null);

  useEffect(() => {
    if (!Number.isFinite(planId)) return;
    let live = true;
    setLoading(true);
    fetchTourPlan(planId)
      .then((d) => {
        if (!live) return;
        setDetail(d);
        setError(null);
      })
      .catch((err) => {
        if (live) setError(statusText(err, { ...ACCESS_TEXTS, 404: "That tour plan was not found." }, "Could not load the plan."));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [planId]);

  return { loading, error, detail };
}
