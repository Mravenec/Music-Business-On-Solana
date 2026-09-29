import type { Course, ReviewStatus } from "../services/courseService";

export const REVIEW_LABEL: Record<ReviewStatus, string> = {
  draft: "draft",
  submitted: "in review",
  approved: "approved",
  rejected: "changes requested",
};

/** One sentence about where an instructor course is in review; nothing for an owner course never submitted. */
export function CourseReviewBanner({ course, teach }: { course: Course; teach: boolean }) {
  if (course.reviewStatus === "submitted") {
    return (
      <div className="eh8s-banner warn">
        {teach
          ? "Waiting for the studio owner's review. Editing is locked until they answer."
          : "An instructor submitted this course. Approve or request changes from the review queue."}
      </div>
    );
  }
  if (course.reviewStatus === "rejected") {
    return (
      <div className="eh8s-banner warn">
        Changes requested{course.reviewNote ? `: ${course.reviewNote}` : "."}
        {teach ? " Edit the course, then submit it again." : ""}
      </div>
    );
  }
  if (teach && course.status === "published") {
    return <div className="eh8s-banner ok">Published. Students can see it. Ask the studio owner for changes.</div>;
  }
  return null;
}
