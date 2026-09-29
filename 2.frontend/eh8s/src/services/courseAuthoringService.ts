import { apiClient as client } from "./http";
import type { Course, CourseSection, Lesson } from "./courseService";

/** Who is authoring: the platform owner (/api/owner) or an instructor (/api/teach, own drafts only). */
export type AuthoringScope = "owner" | "teach";

const BASE: Record<AuthoringScope, string> = { owner: "/api/owner", teach: "/api/teach" };

/** course_access_grant row (JOOQ POJO JSON). courseId null = every course. */
export type CourseAccessGrant = {
  id: number;
  courseId: number | null;
  courseScope: number;
  walletPubkey: string;
  grantedByAccountId: number;
  note: string | null;
  expiresAt: string | null;
  isActive: number;
  createdAt: string;
  updatedAt: string;
};

export type OwnerCourseRow = { course: Course; authorName: string | null; sectionCount: number; lessonCount: number };

export type AuthoringOutline = {
  course: Course;
  sections: { section: CourseSection; lessons: Lesson[] }[];
};

export type CourseInput = {
  title?: string;
  summary?: string | null;
  instrumentId?: number | null;
  minLevel?: number;
};

/** lesson POJO keys; flags are 0 | 1 like the table. */
export type LessonInput = {
  title?: string;
  videoUrl?: string;
  description?: string | null;
  durationSec?: number | null;
  isFreePreview?: number;
  resources?: string | null;
  practicePrompt?: string | null;
  isActive?: number;
};

/** course_access_grant POJO keys; expiresAt is a LocalDateTime ("YYYY-MM-DDTHH:mm:ss"). */
export type GrantInput = {
  walletPubkey: string;
  courseId?: number | null;
  expiresAt?: string | null;
  note?: string | null;
};

export async function fetchOwnerCourses(scope: AuthoringScope = "owner"): Promise<OwnerCourseRow[]> {
  const { data } = await client.get<OwnerCourseRow[]>(`${BASE[scope]}/courses`);
  return data;
}

export async function fetchOwnerCourse(courseId: number, scope: AuthoringScope = "owner"): Promise<AuthoringOutline> {
  const { data } = await client.get<AuthoringOutline>(`${BASE[scope]}/courses/${courseId}`);
  return data;
}

export async function createCourse(input: CourseInput, scope: AuthoringScope = "owner"): Promise<Course> {
  const { data } = await client.post<Course>(`${BASE[scope]}/courses`, input);
  return data;
}

/** 409 for an instructor once the course is submitted or published. */
export async function updateCourse(courseId: number, input: CourseInput, scope: AuthoringScope = "owner"): Promise<Course> {
  const { data } = await client.put<Course>(`${BASE[scope]}/courses/${courseId}`, input);
  return data;
}

/** Owner only. 409 when publishing a course without an active lesson. */
export async function setCourseStatus(
  courseId: number,
  action: "publish" | "archive" | "draft"
): Promise<Course> {
  const { data } = await client.post<Course>(`/api/owner/courses/${courseId}/${action}`);
  return data;
}

export async function createSection(courseId: number, title: string, scope: AuthoringScope = "owner"): Promise<CourseSection> {
  const { data } = await client.post<CourseSection>(`${BASE[scope]}/courses/${courseId}/sections`, { title });
  return data;
}

export async function updateSection(
  sectionId: number,
  input: { title?: string; isActive?: number },
  scope: AuthoringScope = "owner"
): Promise<CourseSection> {
  const { data } = await client.put<CourseSection>(`${BASE[scope]}/sections/${sectionId}`, input);
  return data;
}

export async function moveSection(sectionId: number, sortOrder: number, scope: AuthoringScope = "owner"): Promise<CourseSection[]> {
  const { data } = await client.post<CourseSection[]>(`${BASE[scope]}/sections/${sectionId}/move`, { sortOrder });
  return data;
}

/** 400 when videoUrl is not a YouTube, Vimeo, Bunny Stream or Cloudflare Stream link. */
export async function createLesson(sectionId: number, input: LessonInput, scope: AuthoringScope = "owner"): Promise<Lesson> {
  const { data } = await client.post<Lesson>(`${BASE[scope]}/sections/${sectionId}/lessons`, input);
  return data;
}

export async function updateLesson(lessonId: number, input: LessonInput, scope: AuthoringScope = "owner"): Promise<Lesson> {
  const { data } = await client.put<Lesson>(`${BASE[scope]}/lessons/${lessonId}`, input);
  return data;
}

export async function moveLesson(lessonId: number, sortOrder: number, scope: AuthoringScope = "owner"): Promise<Lesson[]> {
  const { data } = await client.post<Lesson[]>(`${BASE[scope]}/lessons/${lessonId}/move`, { sortOrder });
  return data;
}

export async function fetchCourseGrants(): Promise<CourseAccessGrant[]> {
  const { data } = await client.get<CourseAccessGrant[]>("/api/owner/course-grants");
  return data;
}

export async function grantCourseAccess(input: GrantInput): Promise<CourseAccessGrant> {
  const { data } = await client.post<CourseAccessGrant>("/api/owner/course-grants", input);
  return data;
}

export async function revokeCourseAccess(grantId: number): Promise<CourseAccessGrant> {
  const { data } = await client.post<CourseAccessGrant>(`/api/owner/course-grants/${grantId}/revoke`);
  return data;
}

/** Client-side hint only; the backend is the source of truth for accepted links. */
export function guessProvider(url: string): string | null {
  let host: string;
  try {
    const u = new URL(url.trim());
    if (u.protocol !== "https:") return null;
    host = u.hostname.toLowerCase();
  } catch {
    return null;
  }
  if (["youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com"].includes(host)) {
    return "YouTube";
  }
  if (["vimeo.com", "www.vimeo.com", "player.vimeo.com"].includes(host)) return "Vimeo";
  if (["iframe.mediadelivery.net", "video.bunnycdn.com"].includes(host)) return "Bunny Stream";
  if (host === "iframe.videodelivery.net" || host === "watch.cloudflarestream.com" || /^customer-[a-z0-9]+\.cloudflarestream\.com$/.test(host)) {
    return "Cloudflare Stream";
  }
  return null;
}

/** "m:ss", "h:mm:ss" or plain seconds -> seconds; null when empty or invalid. */
export function parseDuration(text: string): number | null {
  const t = text.trim();
  if (!t) return null;
  if (/^\d+$/.test(t)) return Number(t);
  const parts = t.split(":").map((p) => Number(p));
  if (parts.some((p) => !Number.isInteger(p) || p < 0) || parts.length > 3) return null;
  return parts.reduce((acc, p) => acc * 60 + p, 0);
}
