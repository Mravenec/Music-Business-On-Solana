import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { lockText, useLesson } from "../hooks/useCourses";
import { formatDuration, isSafeEmbed, PROVIDER_LABEL } from "../services/courseService";

/**
 * Job: watch one lesson.
 * Primary: Mark complete and continue (next lesson, or back to the course after the last one).
 * Next: /academy/lessons/:nextLessonId or /academy/courses/:courseId
 * Also: Send practice to NEXUS (/academy/nexus/new?lessonId=…) when the lesson has a practice exercise.
 * Hidden: other sections, raw video links, resources until opened.
 */
export function AcademyLessonPage() {
  const { lessonId = "" } = useParams();
  const navigate = useNavigate();
  const { loading, error, view, complete } = useLesson(Number(lessonId));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const lesson = view?.lesson;
  const done = Boolean(view?.progress?.completedAt);
  const embed = lesson && isSafeEmbed(lesson.embedUrl) ? lesson.embedUrl : null;

  const next = () =>
    navigate(view?.nextLessonId ? `/academy/lessons/${view.nextLessonId}` : `/academy/courses/${view?.course.id}`);

  const onComplete = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      await complete();
      next();
    } catch (err) {
      setSaveError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">
        {view ? (
          <Link to={`/academy/courses/${view.course.id}`}>{view.course.title}</Link>
        ) : (
          "Lesson"
        )}
        {view ? ` · ${view.section.title}` : ""}
      </p>
        <h1>{lesson?.title ?? "Lesson"}</h1>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading lesson…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {view && lesson ? (
        view.locked ? (
          <div className="eh8s-banner warn">
            {lockText(view.lockReason, view.course.minLevel)}{" "}
            {view.lockReason === "plan" ? (
              <Link to="/academy">See plans</Link>
            ) : (
              <Link to="/academy/nexus">Raise your level with NEXUS</Link>
            )}
          </div>
        ) : (
          <>
            {embed ? (
              <div className="eh8s-player">
                <iframe
                  src={embed}
                  title={lesson.title}
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                  allowFullScreen
                  referrerPolicy="strict-origin-when-cross-origin"
                />
              </div>
            ) : (
              <div className="eh8s-banner bad">This video link cannot be played here.</div>
            )}
            <p className="eh8s-muted-line">
              {PROVIDER_LABEL[lesson.videoProvider]}
              {lesson.durationSec ? ` · ${formatDuration(lesson.durationSec)}` : ""}
              {done ? " · Completed" : ""}
            </p>
            {saveError ? <div className="eh8s-banner bad">{saveError}</div> : null}
            <div className="eh8s-cta-row">
              {done ? (
                <button type="button" className="eh8s-btn primary" onClick={next}>
                  {view.nextLessonId ? "Next lesson" : "Back to course"}
                </button>
              ) : (
                <button type="button" className="eh8s-btn primary" onClick={onComplete} disabled={saving}>
                  {saving ? "Saving…" : view.nextLessonId ? "Mark complete and continue" : "Mark complete"}
                </button>
              )}
            </div>
            {lesson.description ? <p className="eh8s-lead">{lesson.description}</p> : null}
            {lesson.resources ? (
              <details className="eh8s-panel">
                <summary>Practice notes and resources</summary>
                <p>{lesson.resources}</p>
              </details>
            ) : null}
            {lesson.practicePrompt ? (
              <div className="eh8s-panel">
                <p>
                  <strong>Practice:</strong> {lesson.practicePrompt}
                </p>
                <Link className="eh8s-btn" to={`/academy/nexus/new?lessonId=${lesson.id}`}>
                  Send practice to NEXUS
                </Link>
              </div>
            ) : null}
          </>
        )
      ) : null}
      <div className="eh8s-cta-row">
        {view?.previousLessonId ? (
          <Link className="eh8s-btn" to={`/academy/lessons/${view.previousLessonId}`}>
            Previous lesson
          </Link>
        ) : null}
        {view ? (
          <Link className="eh8s-btn" to={`/academy/courses/${view.course.id}`}>
            Course outline
          </Link>
        ) : null}
      </div>
    </section>
  );
}
