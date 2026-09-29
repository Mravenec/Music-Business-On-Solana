import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { fetchInstruments, fetchLevels, type EnigmaLevel, type Instrument } from "../services/academyService";
import {
  createCourse,
  createLesson,
  createSection,
  fetchCourseGrants,
  fetchOwnerCourse,
  fetchOwnerCourses,
  grantCourseAccess,
  moveLesson,
  moveSection,
  revokeCourseAccess,
  setCourseStatus,
  updateCourse,
  updateLesson,
  updateSection,
  type AuthoringOutline,
  type AuthoringScope,
  type CourseAccessGrant,
  type CourseInput,
  type GrantInput,
  type LessonInput,
  type OwnerCourseRow,
} from "../services/courseAuthoringService";
import type { Course, CourseSection, Lesson } from "../services/courseService";
import { approveCourse, rejectCourse, submitCourse } from "../services/teachingService";
import { statusText } from "./statusText";

export const AUTHORING_TEXTS = {
  400: "Check the fields. Video links must be https YouTube, Vimeo, Bunny Stream or Cloudflare Stream links.",
  403: "Only the platform owner can edit courses.",
  404: "That course was not found.",
  409: "Add at least one visible lesson before publishing.",
};

export const TEACHING_TEXTS = {
  ...AUTHORING_TEXTS,
  403: "Teaching needs Enigma level 5 and an approved instructor role, and you can only edit your own courses.",
  409: "This course is locked while it waits for review or after it is published. Submitting needs one visible lesson.",
};

/** Turns an authoring API error into one sentence for the screen. */
export function authoringError(err: unknown, fallback: string, scope: AuthoringScope = "owner"): string {
  return statusText(err, scope === "teach" ? TEACHING_TEXTS : AUTHORING_TEXTS, fallback);
}

export type AuthoringContext = {
  scope: AuthoringScope;
  /** Route prefix for the course editor pages: /owner/courses or /academy/teach/courses. */
  root: string;
};

/** The editor pages serve the owner and instructors; the URL decides which API they call. */
export function useAuthoringScope(): AuthoringContext {
  const { pathname } = useLocation();
  return pathname.startsWith("/academy/teach")
    ? { scope: "teach", root: "/academy/teach/courses" }
    : { scope: "owner", root: "/owner/courses" };
}

export type OwnerCoursesState = { loading: boolean; error: string | null; rows: OwnerCourseRow[] };

/** Every course the caller can edit, any status (instructors: their own only). */
export function useOwnerCourses(scope: AuthoringScope = "owner"): OwnerCoursesState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<OwnerCourseRow[]>([]);

  useEffect(() => {
    let live = true;
    fetchOwnerCourses(scope)
      .then((data) => {
        if (!live) return;
        setRows(data);
        setError(null);
      })
      .catch((err) => {
        if (live) setError(authoringError(err, "Could not load courses.", scope));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [scope]);

  return { loading, error, rows };
}

/** Mutations on one course, bound to its id and the authoring scope. */
export type CourseActions = {
  createSection: (title: string) => Promise<CourseSection>;
  updateSection: (sectionId: number, input: { title?: string; isActive?: number }) => Promise<CourseSection>;
  moveSection: (sectionId: number, sortOrder: number) => Promise<CourseSection[]>;
  createLesson: (sectionId: number, input: LessonInput) => Promise<Lesson>;
  updateLesson: (lessonId: number, input: LessonInput) => Promise<Lesson>;
  moveLesson: (lessonId: number, sortOrder: number) => Promise<Lesson[]>;
  /** Owner only: publish, back to draft, or archive. */
  setStatus: (action: "publish" | "archive" | "draft") => Promise<Course>;
  /** Instructor: send the draft to the owner review queue. */
  submit: () => Promise<Course>;
  /** Owner review: publish an instructor course. */
  approve: () => Promise<Course>;
  /** Owner review: send the course back with a note. */
  reject: (note: string) => Promise<Course>;
};

export type OwnerCourseState = {
  loading: boolean;
  error: string | null;
  outline: AuthoringOutline | null;
  reload: () => Promise<void>;
  /** Runs one mutation, reloads the outline, returns an error sentence or null. */
  run: (action: () => Promise<unknown>, fallback: string) => Promise<string | null>;
  actions: CourseActions;
};

/** Full authoring outline of one course (inactive sections and lessons included). */
export function useOwnerCourse(courseId: number, scope: AuthoringScope = "owner"): OwnerCourseState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [outline, setOutline] = useState<AuthoringOutline | null>(null);

  const reload = useCallback(async () => {
    if (!Number.isFinite(courseId)) return;
    try {
      setOutline(await fetchOwnerCourse(courseId, scope));
      setError(null);
    } catch (err) {
      setError(authoringError(err, "Could not load the course.", scope));
    } finally {
      setLoading(false);
    }
  }, [courseId, scope]);

  useEffect(() => {
    setLoading(true);
    void reload();
  }, [reload]);

  const actions = useMemo<CourseActions>(
    () => ({
      createSection: (title) => createSection(courseId, title, scope),
      updateSection: (sectionId, input) => updateSection(sectionId, input, scope),
      moveSection: (sectionId, sortOrder) => moveSection(sectionId, sortOrder, scope),
      createLesson: (sectionId, input) => createLesson(sectionId, input, scope),
      updateLesson: (lessonId, input) => updateLesson(lessonId, input, scope),
      moveLesson: (lessonId, sortOrder) => moveLesson(lessonId, sortOrder, scope),
      setStatus: (action) => setCourseStatus(courseId, action),
      submit: () => submitCourse(courseId),
      approve: () => approveCourse(courseId),
      reject: (note) => rejectCourse(courseId, note),
    }),
    [courseId, scope],
  );

  return {
    loading,
    error,
    outline,
    reload,
    actions,
    run: async (action, fallback) => {
      try {
        await action();
        await reload();
        return null;
      } catch (err) {
        return authoringError(err, fallback, scope);
      }
    },
  };
}

export type CourseGrantsState = {
  loading: boolean;
  error: string | null;
  grants: CourseAccessGrant[];
  reload: () => Promise<void>;
};

/** Owner-issued course access grants, newest first. */
export function useCourseGrants(): CourseGrantsState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [grants, setGrants] = useState<CourseAccessGrant[]>([]);

  const reload = useCallback(async () => {
    try {
      setGrants(await fetchCourseGrants());
      setError(null);
    } catch (err) {
      setError(statusText(err, { 403: "Only the platform owner can manage course access." }, "Could not load access grants."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { loading, error, grants, reload };
}

export type CourseGrantActions = {
  grant: (input: GrantInput) => Promise<CourseAccessGrant>;
  revoke: (grantId: number) => Promise<CourseAccessGrant>;
};

/** Owner grants and revokes free course access for one wallet. */
export function useCourseGrantActions(): CourseGrantActions {
  return useMemo(() => ({ grant: grantCourseAccess, revoke: revokeCourseAccess }), []);
}

export type CourseFormState = {
  instruments: Instrument[];
  levels: EnigmaLevel[];
  /** The course being edited once loaded; null for a new course. */
  course: Course | null;
  error: string | null;
  save: (input: CourseInput) => Promise<Course>;
};

/** Course details form: instrument and level choices, the course when editing, and save. */
export function useCourseForm(courseId: number | null, scope: AuthoringScope = "owner"): CourseFormState {
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [levels, setLevels] = useState<EnigmaLevel[]>([]);
  const [course, setCourse] = useState<Course | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchInstruments().then(setInstruments).catch(() => setInstruments([]));
    fetchLevels().then(setLevels).catch(() => setLevels([]));
  }, []);

  useEffect(() => {
    if (courseId === null) return;
    fetchOwnerCourse(courseId, scope)
      .then((outline) => setCourse(outline.course))
      .catch((err) => setError(authoringError(err, "Could not load the course.", scope)));
  }, [courseId, scope]);

  const save = useCallback(
    (input: CourseInput) => (courseId === null ? createCourse(input, scope) : updateCourse(courseId, input, scope)),
    [courseId, scope],
  );

  return { instruments, levels, course, error, save };
}
