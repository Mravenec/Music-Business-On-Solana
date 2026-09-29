import { useCallback, useEffect, useState } from "react";
import {
  fetchCourse,
  fetchCourses,
  fetchLesson,
  saveLessonProgress,
  type CatalogRow,
  type CourseOutline,
  type LessonView,
  type LockReason,
  type ProgressResult,
} from "../services/courseService";
import { statusText } from "./statusText";

/** Plain words for a locked lesson or course. */
export function lockText(reason: LockReason, minLevel: number): string {
  if (reason === "plan") return "Needs an active academy plan.";
  if (reason === "level") return `Needs Enigma level ${minLevel} or higher.`;
  return "";
}

export type CourseCatalogState = { loading: boolean; error: string | null; rows: CatalogRow[] };

/** Published courses with the signed-in student's progress. */
export function useCourseCatalog(): CourseCatalogState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<CatalogRow[]>([]);

  useEffect(() => {
    let live = true;
    fetchCourses()
      .then((data) => {
        if (!live) return;
        setRows(data);
        setError(null);
      })
      .catch((err) => {
        if (live) setError(statusText(err, {}, "Could not load courses."));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, []);

  return { loading, error, rows };
}

export type CourseOutlineState = {
  loading: boolean;
  error: string | null;
  outline: CourseOutline | null;
  /** First unlocked lesson not completed yet, else the first unlocked lesson. */
  continueLessonId: number | null;
};

/** One course outline (sections, lessons, locks, completion). */
export function useCourseOutline(courseId: number): CourseOutlineState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [outline, setOutline] = useState<CourseOutline | null>(null);

  useEffect(() => {
    if (!Number.isFinite(courseId)) return;
    let live = true;
    setLoading(true);
    fetchCourse(courseId)
      .then((data) => {
        if (!live) return;
        setOutline(data);
        setError(null);
      })
      .catch((err) => {
        if (live) setError(statusText(err, { 404: "That course is not available." }, "Could not load the course."));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [courseId]);

  const open = (outline?.sections ?? []).flatMap((s) => s.lessons).filter((l) => !l.locked);
  const next = open.find((l) => !l.completed) ?? open[0];
  return { loading, error, outline, continueLessonId: next ? next.lesson.id : null };
}

export type LessonState = {
  loading: boolean;
  error: string | null;
  view: LessonView | null;
  complete: () => Promise<ProgressResult>;
};

/** One lesson for the player, plus "mark complete". */
export function useLesson(lessonId: number): LessonState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<LessonView | null>(null);

  const load = useCallback(async () => {
    if (!Number.isFinite(lessonId)) return;
    setLoading(true);
    try {
      setView(await fetchLesson(lessonId));
      setError(null);
    } catch (err) {
      setError(statusText(err, { 404: "That lesson is not available." }, "Could not load the lesson."));
    } finally {
      setLoading(false);
    }
  }, [lessonId]);

  useEffect(() => {
    void load();
  }, [load]);

  return {
    loading,
    error,
    view,
    complete: async () => {
      try {
        const out = await saveLessonProgress(lessonId, view?.lesson.durationSec ?? 0, true);
        setView((v) => (v ? { ...v, progress: out.progress } : v));
        return out;
      } catch (err) {
        throw new Error(
          statusText(err, { 403: "This lesson is locked for your account." }, "Could not save your progress.")
        );
      }
    },
  };
}
