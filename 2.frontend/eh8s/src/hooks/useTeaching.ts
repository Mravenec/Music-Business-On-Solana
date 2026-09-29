import { useCallback, useEffect, useState } from "react";
import type { OwnerCourseRow } from "../services/courseAuthoringService";
import { fetchCourseReviews, fetchEligibility, type TeachEligibility } from "../services/teachingService";
import { statusText } from "./statusText";

export type EligibilityState = { loading: boolean; error: string | null; eligibility: TeachEligibility | null };

/** Whether the signed-in account may teach, with one line per unmet requirement. */
export function useTeachEligibility(): EligibilityState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [eligibility, setEligibility] = useState<TeachEligibility | null>(null);

  useEffect(() => {
    let live = true;
    fetchEligibility()
      .then((data) => {
        if (!live) return;
        setEligibility(data);
        setError(null);
      })
      .catch((err) => {
        if (live) setError(statusText(err, {}, "Could not check your teaching status."));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, []);

  return { loading, error, eligibility };
}

export type CourseReviewsState = {
  loading: boolean;
  error: string | null;
  rows: OwnerCourseRow[];
  reload: () => Promise<void>;
};

/** Owner review queue: instructor courses waiting for approval. */
export function useCourseReviews(): CourseReviewsState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<OwnerCourseRow[]>([]);

  const reload = useCallback(async () => {
    try {
      setRows(await fetchCourseReviews());
      setError(null);
    } catch (err) {
      setError(statusText(err, { 403: "Only the platform owner can review courses." }, "Could not load the review queue."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { loading, error, rows, reload };
}
