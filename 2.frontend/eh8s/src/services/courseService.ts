import { apiClient as client } from "./http";

/** course row (JOOQ POJO JSON). */
/** Instructor review lifecycle; owner-made courses are approved when published. */
export type ReviewStatus = "draft" | "submitted" | "approved" | "rejected";

export type Course = {
  id: number;
  title: string;
  summary: string | null;
  instrumentId: number | null;
  minLevel: number;
  status: "draft" | "published" | "archived";
  reviewStatus: ReviewStatus;
  reviewNote: string | null;
  reviewedByAccountId: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  authorAccountId: number;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
};

/** course_section row (JOOQ POJO JSON). */
export type CourseSection = {
  id: number;
  courseId: number;
  title: string;
  sortOrder: number;
  isActive: number;
  createdAt: string;
};

/** lesson row (JOOQ POJO JSON). videoUrl / embedUrl are null when the lesson is locked. */
export type Lesson = {
  id: number;
  sectionId: number;
  title: string;
  description: string | null;
  videoProvider: "youtube" | "vimeo" | "bunny" | "cloudflare";
  videoUrl: string | null;
  embedUrl: string | null;
  /** SHA-256 hex of the official object; owner outline only (stripped for students). */
  contentHash?: string | null;
  /** Relative storage object key; owner outline only. */
  storageRef?: string | null;
  /** Encryption key id; owner outline only. */
  keyId?: string | null;
  durationSec: number | null;
  sortOrder: number;
  isFreePreview: number;
  resources: string | null;
  /** Exercise students send to NEXUS; null when the lesson is locked. */
  practicePrompt: string | null;
  isActive: number;
  createdAt: string;
  updatedAt: string;
};

/** lesson_progress row (JOOQ POJO JSON). */
export type LessonProgress = {
  id: number;
  accountId: number;
  lessonId: number;
  lastPositionSec: number;
  completedAt: string | null;
  updatedAt: string;
};

export type LockReason = "plan" | "level" | null;

export type CourseAccess = {
  canWatch: boolean;
  lockReason: LockReason;
  activePlan: boolean;
  level: number | null;
  minLevel: number;
};

export type CatalogRow = {
  course: Course;
  lessonCount: number;
  totalDurationSec: number;
  percent: number;
  access: CourseAccess;
};

export type OutlineLesson = { lesson: Lesson; locked: boolean; completed: boolean };

export type CourseOutline = {
  course: Course;
  sections: { section: CourseSection; lessons: OutlineLesson[] }[];
  lessonCount: number;
  percent: number;
  access: CourseAccess;
};

export type LessonView = {
  lesson: Lesson;
  locked: boolean;
  lockReason: LockReason;
  /** True when any official media field is pinned (student GET never returns those fields). */
  official?: boolean;
  course: Course;
  section: CourseSection;
  progress: LessonProgress | null;
  previousLessonId: number | null;
  nextLessonId: number | null;
};

/** GET /api/lessons/:id/watch — embed (YouTube free preview / legacy) or short-lived signed URL. */
export type LessonWatch = {
  lessonId: number;
  kind: "embed" | "signed";
  url: string;
  expiresAt: string | null;
  contentHash: string | null;
  keyId: string | null;
};

export type ProgressResult = { progress: LessonProgress; percent: number };

export async function fetchCourses(): Promise<CatalogRow[]> {
  const { data } = await client.get<CatalogRow[]>("/api/courses");
  return data;
}

export async function fetchCourse(courseId: number): Promise<CourseOutline> {
  const { data } = await client.get<CourseOutline>(`/api/courses/${courseId}`);
  return data;
}

export async function fetchLesson(lessonId: number): Promise<LessonView> {
  const { data } = await client.get<LessonView>(`/api/lessons/${lessonId}`);
  return data;
}

/**
 * Playback URL after plan/level gates.
 * Official media → kind signed (temporary); free preview / legacy → kind embed (YouTube etc.).
 * 403 locked · 409 official but half-pinned.
 */
export async function fetchLessonWatch(lessonId: number): Promise<LessonWatch> {
  const { data } = await client.get<LessonWatch>(`/api/lessons/${lessonId}/watch`);
  return data;
}

/** 403 when the lesson is locked for the caller. */
export async function saveLessonProgress(
  lessonId: number,
  positionSec: number,
  completed: boolean
): Promise<ProgressResult> {
  const { data } = await client.put<ProgressResult>(`/api/lessons/${lessonId}/progress`, {
    positionSec,
    completed,
  });
  return data;
}

const EMBED_HOSTS = [
  "https://www.youtube-nocookie.com/embed/",
  "https://player.vimeo.com/video/",
  "https://iframe.mediadelivery.net/embed/",
  "https://iframe.videodelivery.net/",
];

/** Only player URLs produced by the backend normalizer are ever put in an iframe. */
export function isSafeEmbed(url: string | null): url is string {
  if (!url) return false;
  if (EMBED_HOSTS.some((prefix) => url.startsWith(prefix))) return true;
  return /^https:\/\/customer-[a-z0-9]{1,40}\.cloudflarestream\.com\/[0-9a-f]{32}\/iframe$/.test(url);
}

export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds) return "";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const mm = h ? String(m).padStart(2, "0") : String(m);
  return `${h ? `${h}:` : ""}${mm}:${String(s).padStart(2, "0")}`;
}

export const PROVIDER_LABEL: Record<Lesson["videoProvider"], string> = {
  youtube: "YouTube",
  vimeo: "Vimeo",
  bunny: "Bunny Stream",
  cloudflare: "Cloudflare Stream",
};
