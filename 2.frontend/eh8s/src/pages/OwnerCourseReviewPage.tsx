import { FormEvent, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useOwnerCourse } from "../hooks/useCourseAuthoring";
import { statusText } from "../hooks/statusText";
import { formatDuration, isSafeEmbed, PROVIDER_LABEL } from "../services/courseService";

const REVIEW_TEXTS = {
  400: "Write a short note so the instructor knows what to change.",
  403: "Only the platform owner can review courses.",
  409: "This course is no longer waiting for review.",
};

/**
 * Job: approve or send back one submitted course.
 * Primary: Approve and publish.
 * Next: /owner/courses/review (after a decision) · /owner/courses/:id (full outline)
 * Hidden: editing controls, access grants; each lesson video opens on click.
 */
export function OwnerCourseReviewPage() {
  const { courseId = "" } = useParams();
  const id = Number(courseId);
  const navigate = useNavigate();
  const { loading, error, outline, actions } = useOwnerCourse(id);
  const [note, setNote] = useState("");
  const [open, setOpen] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const course = outline?.course;
  const submitted = course?.reviewStatus === "submitted";

  async function decide(action: () => Promise<unknown>, fallback: string) {
    setBusy(true);
    setMsg(null);
    try {
      await action();
      navigate("/owner/courses/review");
    } catch (err) {
      setMsg(statusText(err, REVIEW_TEXTS, fallback));
    } finally {
      setBusy(false);
    }
  }

  function onReject(e: FormEvent) {
    e.preventDefault();
    const text = note.trim();
    if (!text) {
      setMsg(REVIEW_TEXTS[400]);
      return;
    }
    void decide(() => actions.reject(text), "Could not send the course back.");
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">
        <Link to="/owner/courses/review">Review queue</Link>
      </p>
      <h1>{course?.title ?? "Course review"}</h1>
      {course ? (
        <p className="eh8s-lead">
          Level {course.minLevel}+{course.summary ? ` · ${course.summary}` : ""}
        </p>
      ) : null}
      {loading ? <p className="eh8s-muted-line">Loading course…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {course && !submitted ? <div className="eh8s-banner warn">{REVIEW_TEXTS[409]}</div> : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      {outline?.sections.map(({ section, lessons }, sIndex) => (
        <div key={section.id} className="eh8s-outline">
          <h2>
            {sIndex + 1}. {section.title}
            {section.isActive ? "" : " (hidden)"}
          </h2>
          <ol>
            {lessons.map((lesson) => (
              <li key={lesson.id} className={lesson.isActive ? "" : "locked"}>
                <button type="button" className="eh8s-btn-mini" onClick={() => setOpen(open === lesson.id ? null : lesson.id)}>
                  {open === lesson.id ? "Hide" : "Watch"}
                </button>{" "}
                {lesson.title}
                <span className="eh8s-outline-meta">
                  {lesson.isFreePreview ? <span className="eh8s-tag">Free preview</span> : null}
                  {lesson.practicePrompt ? <span className="eh8s-tag">Practice</span> : null}
                  {PROVIDER_LABEL[lesson.videoProvider]} {formatDuration(lesson.durationSec)}
                </span>
                {open === lesson.id ? (
                  <>
                    {isSafeEmbed(lesson.embedUrl) ? (
                      <div className="eh8s-player">
                        <iframe
                          src={lesson.embedUrl ?? undefined}
                          title={lesson.title}
                          allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                          allowFullScreen
                          referrerPolicy="strict-origin-when-cross-origin"
                        />
                      </div>
                    ) : null}
                    {lesson.practicePrompt ? <p className="eh8s-muted-line">Practice: {lesson.practicePrompt}</p> : null}
                  </>
                ) : null}
              </li>
            ))}
          </ol>
        </div>
      ))}
      {submitted ? (
        <>
          <div className="eh8s-cta-row">
            <button
              type="button"
              className="eh8s-btn primary"
              disabled={busy}
              onClick={() => decide(() => actions.approve(), "Could not approve the course.")}
            >
              Approve and publish
            </button>
          </div>
          <form className="eh8s-form eh8s-panel" onSubmit={onReject}>
            <label>
              Request changes (the instructor sees this note)
              <textarea maxLength={500} value={note} onChange={(ev) => setNote(ev.target.value)} />
            </label>
            <button type="submit" className="eh8s-btn" disabled={busy}>
              Send back with note
            </button>
          </form>
        </>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/owner/courses/${id}`}>
          Full outline
        </Link>
      </div>
    </section>
  );
}
