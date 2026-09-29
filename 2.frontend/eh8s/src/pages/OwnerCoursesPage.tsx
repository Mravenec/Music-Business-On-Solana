import { Link } from "react-router-dom";
import { useAuthoringScope, useOwnerCourses } from "../hooks/useCourseAuthoring";
import { REVIEW_LABEL } from "./CourseReviewBanner";

const STATUS_BADGE: Record<string, string> = { published: "ok", draft: "info", archived: "muted" };

/**
 * Job: pick a course to work on (owner: every course · instructor: own courses at /academy/teach/courses).
 * Primary: New course.
 * Next: {root}/new or {root}/:courseId (owner also: /owner/courses/review, /owner/course-access)
 * Hidden: sections, lessons, video links, access grants (one click away).
 */
export function OwnerCoursesPage() {
  const { scope, root } = useAuthoringScope();
  const { loading, error, rows } = useOwnerCourses(scope);
  const owner = scope === "owner";
  const waiting = rows.filter((r) => r.course.reviewStatus === "submitted").length;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">{owner ? "Studio" : "Teach"}</p>
        <h1>{owner ? "Courses" : "My courses"}</h1>
        <p className="eh8s-lead">
          Paste a YouTube, Vimeo, Bunny Stream or Cloudflare Stream link per lesson. Videos stay on that host.
          {owner ? "" : " The studio owner reviews each course before students see it."}
        </p>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading courses…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to={`${root}/new`}>
          New course
        </Link>
        {owner ? (
          <>
            <Link className="eh8s-btn" to="/owner/courses/review">
              Review queue{waiting ? ` (${waiting})` : ""}
            </Link>
            <Link className="eh8s-btn" to="/owner/course-access">
              Course access
            </Link>
          </>
        ) : null}
      </div>
      <div className="eh8s-band-grid">
        {rows.map(({ course, authorName, sectionCount, lessonCount }) => (
          <article key={course.id} className="eh8s-band-card">
            <div className="eh8s-band-card-top">
              <span className="eh8s-band-id">Level {course.minLevel}+</span>
              <span className={`eh8s-badge ${STATUS_BADGE[course.status] ?? "muted"}`}>
                {course.status === "draft" && course.reviewStatus !== "draft"
                  ? REVIEW_LABEL[course.reviewStatus]
                  : course.status}
              </span>
            </div>
            <h3>{course.title}</h3>
            <p className="eh8s-muted-line">
              {sectionCount} sections · {lessonCount} lessons
              {owner && authorName ? ` · by ${authorName}` : ""}
            </p>
            <Link className="eh8s-btn" to={`${root}/${course.id}`}>
              {owner ? "Edit course" : "Open course"}
            </Link>
          </article>
        ))}
        {!rows.length && !loading && !error ? (
          <p className="eh8s-empty">No courses yet. Start with one course and one free preview lesson.</p>
        ) : null}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={owner ? "/owner" : "/academy/teach"}>
          {owner ? "Back to inbox" : "Back to Teach"}
        </Link>
      </div>
    </section>
  );
}
