import { Link } from "react-router-dom";
import { lockText, useCourseCatalog } from "../hooks/useCourses";
import { formatDuration } from "../services/courseService";

/**
 * Job: pick a video course.
 * Primary: Open course.
 * Next: /academy/courses/:courseId
 * Hidden: sections, lessons, video player, progress per lesson.
 */
export function AcademyCoursesPage() {
  const { loading, error, rows } = useCourseCatalog();

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Academy</p>
        <h1>Video courses</h1>
        <p className="eh8s-lead">Lessons from the studio. Your plan and Enigma level open the full library.</p>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading courses…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      <div className="eh8s-band-grid">
        {rows.map((row) => (
          <article key={row.course.id} className="eh8s-band-card">
            <div className="eh8s-band-card-top">
              <span className="eh8s-band-id">Level {row.course.minLevel}+</span>
              {row.access.canWatch ? (
                <span className="eh8s-badge ok">Open</span>
              ) : (
                <span className="eh8s-badge muted">Preview only</span>
              )}
            </div>
            <h3>{row.course.title}</h3>
            {row.course.summary ? <p className="eh8s-band-code">{row.course.summary}</p> : null}
            <p className="eh8s-muted-line">
              {row.lessonCount} lessons
              {row.totalDurationSec ? ` · ${formatDuration(row.totalDurationSec)}` : ""}
              {row.access.canWatch ? "" : ` · ${lockText(row.access.lockReason, row.access.minLevel)}`}
            </p>
            <div className="eh8s-meter" aria-label={`${row.percent}% complete`}>
              <span style={{ width: `${row.percent}%` }} />
            </div>
            <Link className="eh8s-btn primary" to={`/academy/courses/${row.course.id}`}>
              Open course
            </Link>
          </article>
        ))}
        {!rows.length && !loading && !error ? (
          <p className="eh8s-empty">No courses are published yet. New lessons appear here first.</p>
        ) : null}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/academy">
          Back to academy
        </Link>
      </div>
    </section>
  );
}
