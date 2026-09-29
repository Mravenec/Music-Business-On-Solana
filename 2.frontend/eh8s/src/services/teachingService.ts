import { apiClient as client } from "./http";
import type { Course } from "./courseService";
import type { OwnerCourseRow } from "./courseAuthoringService";

export type TeachRequirement = { key: "level" | "instructor"; label: string; met: boolean; detail: string };

/** GET /api/teach/eligibility — the two gates to teach (Enigma level 5 + approved instructor role). */
export type TeachEligibility = {
  canTeach: boolean;
  owner: boolean;
  level: number | null;
  requiredLevel: number;
  instructor: boolean;
  requirements: TeachRequirement[];
};

export async function fetchEligibility(): Promise<TeachEligibility> {
  const { data } = await client.get<TeachEligibility>("/api/teach/eligibility");
  return data;
}

/** Author only. 409 when the course is not an editable draft or has no visible lesson. */
export async function submitCourse(courseId: number): Promise<Course> {
  const { data } = await client.post<Course>(`/api/teach/courses/${courseId}/submit`);
  return data;
}

/** Owner only: submitted courses, oldest first. */
export async function fetchCourseReviews(): Promise<OwnerCourseRow[]> {
  const { data } = await client.get<OwnerCourseRow[]>("/api/owner/course-reviews");
  return data;
}

/** Owner only. Publishes the course; 409 when it is not submitted. */
export async function approveCourse(courseId: number): Promise<Course> {
  const { data } = await client.post<Course>(`/api/owner/courses/${courseId}/approve`);
  return data;
}

/** Owner only. 400 without a note; the course goes back to the author as a draft. */
export async function rejectCourse(courseId: number, note: string): Promise<Course> {
  const { data } = await client.post<Course>(`/api/owner/courses/${courseId}/reject`, { reviewNote: note });
  return data;
}
