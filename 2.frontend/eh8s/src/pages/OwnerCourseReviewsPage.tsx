import { Link } from "react-router-dom";
import { useCourseReviews } from "../hooks/useTeaching";

/**
 * Job: see which instructor courses wait for a decision.
 * Primary: Review (oldest submission first).
 * Next: /owner/courses/review/:courseId
 * Hidden: approve / reject controls and the lesson list (on the review screen).
 */
export function OwnerCourseReviewsPage() {
  const { loading, error, rows } = useCourseReviews();

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">
        <Link to="/owner/courses">Courses</Link>
      </p>
        <h1>Review queue</h1>
        <p className="eh8s-lead">Instructor courses reach students only after you approve them.</p>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading submissions…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      <div className="eh8s-band-grid">
        {rows.map(({ course, authorName, sectionCount, lessonCount }, i) => (
          <article key={course.id} className="eh8s-band-card">
            <div className="eh8s-band-card-top">
              <span className="eh8s-band-id">Level {course.minLevel}+</span>
              <span className="eh8s-badge info">in review</span>
            </div>
            <h3>{course.title}</h3>
            <p className="eh8s-muted-line">
              {authorName ? `by ${authorName} · ` : ""}
              {sectionCount} sections · {lessonCount} lessons
            </p>
            <Link className={i === 0 ? "eh8s-btn primary" : "eh8s-btn"} to={`/owner/courses/review/${course.id}`}>
              Review
            </Link>
          </article>
        ))}
        {!rows.length && !loading && !error ? <p className="eh8s-empty">Nothing waiting for review.</p> : null}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/owner/courses">
          Back to courses
        </Link>
      </div>
    </section>
  );
}
