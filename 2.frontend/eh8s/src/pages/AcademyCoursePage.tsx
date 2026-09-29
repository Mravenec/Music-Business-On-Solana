import { Link, useParams } from "react-router-dom";
import { lockText, useCourseOutline } from "../hooks/useCourses";
import { formatDuration } from "../services/courseService";

/**
 * Job: see what a course teaches and where I left off.
 * Primary: Continue (or Start) — the first unlocked lesson not completed yet.
 * Next: /academy/lessons/:lessonId
 * Hidden: video player, descriptions, resources (they live on the lesson screen).
 */
export function AcademyCoursePage() {
  const { courseId = "" } = useParams();
  const { loading, error, outline, continueLessonId } = useCourseOutline(Number(courseId));
  const access = outline?.access;
  const started = (outline?.percent ?? 0) > 0;

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Video course</p>
      <h1>{outline?.course.title ?? "Course"}</h1>
      {outline?.course.summary ? <p className="eh8s-lead">{outline.course.summary}</p> : null}
      {loading ? <p className="eh8s-muted-line">Loading course…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {outline && access ? (
        <>
          <div className="eh8s-inline-meter">
            <strong>
              {outline.percent}% complete · {outline.lessonCount} lessons
            </strong>
            <div className="eh8s-meter tall">
              <span style={{ width: `${outline.percent}%` }} />
            </div>
          </div>
          {!access.canWatch ? (
            <div className="eh8s-banner warn">
              {lockText(access.lockReason, access.minLevel)} Free previews are open.{" "}
              {access.lockReason === "plan" ? (
                <Link to="/academy">See plans</Link>
              ) : (
                <Link to="/academy/nexus">Raise your level with NEXUS</Link>
              )}
            </div>
          ) : null}
          <div className="eh8s-cta-row">
            {continueLessonId ? (
              <Link className="eh8s-btn primary" to={`/academy/lessons/${continueLessonId}`}>
                {started ? "Continue" : "Start"}
              </Link>
            ) : null}
          </div>
          {outline.sections.map(({ section, lessons }, sIndex) => (
            <div key={section.id} className="eh8s-outline">
              <h2>
                {sIndex + 1}. {section.title}
              </h2>
              <ol>
                {lessons.map(({ lesson, locked, completed }) => (
                  <li key={lesson.id} className={locked ? "locked" : completed ? "done" : ""}>
                    {locked ? (
                      <span>{lesson.title}</span>
                    ) : (
                      <Link to={`/academy/lessons/${lesson.id}`}>{lesson.title}</Link>
                    )}
                    <span className="eh8s-outline-meta">
                      {lesson.isFreePreview && !completed ? <span className="eh8s-tag">Free preview</span> : null}
                      {formatDuration(lesson.durationSec)}
                      {completed ? " · Done" : locked ? " · Locked" : ""}
                    </span>
                  </li>
                ))}
                {!lessons.length ? <li className="eh8s-muted-line">No lessons in this section yet.</li> : null}
              </ol>
            </div>
          ))}
        </>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/academy/courses">
          All courses
        </Link>
      </div>
    </section>
  );
}
