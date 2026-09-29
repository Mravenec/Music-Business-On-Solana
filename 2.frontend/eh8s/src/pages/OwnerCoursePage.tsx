import { FormEvent, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuthoringScope, useOwnerCourse } from "../hooks/useCourseAuthoring";
import { formatDuration, PROVIDER_LABEL } from "../services/courseService";
import { CourseReviewBanner } from "./CourseReviewBanner";

/**
 * Job: shape one course — sections, lesson order, publishing (owner) or review (instructor).
 * Primary: owner — Publish (draft with a visible lesson) · Review (submitted) · Preview as student (published).
 *          instructor — Submit for review (editable draft with a visible lesson).
 *          both — Add section (empty course).
 * Next: {root}/:id/sections/:sectionId · {root}/:id/lessons/:lessonId · /academy/courses/:id · /owner/courses/review/:id
 * Hidden: lesson fields and video links (on the lesson screen), access grants; editing controls while an
 *         instructor course is in review or published.
 */
export function OwnerCoursePage() {
  const { courseId = "" } = useParams();
  const id = Number(courseId);
  const { scope, root } = useAuthoringScope();
  const teach = scope === "teach";
  const { loading, error, outline, run, actions } = useOwnerCourse(id, scope);
  const [sectionTitle, setSectionTitle] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const course = outline?.course;
  const sections = outline?.sections ?? [];
  const visibleLessons = sections
    .filter((s) => s.section.isActive)
    .flatMap((s) => s.lessons)
    .filter((l) => l.isActive).length;
  const editable =
    !teach ||
    (course?.status === "draft" && (course.reviewStatus === "draft" || course.reviewStatus === "rejected"));
  const canPublish = !teach && course?.status !== "published" && visibleLessons > 0;
  const canSubmit = teach && editable && visibleLessons > 0;
  const inReview = course?.reviewStatus === "submitted";

  async function act(action: () => Promise<unknown>, fallback: string) {
    setBusy(true);
    setMsg(await run(action, fallback));
    setBusy(false);
  }

  async function onAddSection(e: FormEvent) {
    e.preventDefault();
    const title = sectionTitle.trim();
    if (!title) return;
    await act(() => actions.createSection(title), "Could not add the section.");
    setSectionTitle("");
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">
        <Link to={root}>{teach ? "My courses" : "Courses"}</Link> · {course?.status ?? ""}
      </p>
      <h1>{course?.title ?? "Course"}</h1>
      {course ? (
        <p className="eh8s-lead">
          Level {course.minLevel}+ · {sections.length} sections · {visibleLessons} visible lessons
        </p>
      ) : null}
      {loading ? <p className="eh8s-muted-line">Loading course…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {course ? <CourseReviewBanner course={course} teach={teach} /> : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      {course ? (
        <>
          <div className="eh8s-cta-row">
            {canPublish && !inReview ? (
              <button
                type="button"
                className="eh8s-btn primary"
                disabled={busy}
                onClick={() => act(() => actions.setStatus("publish"), "Could not publish.")}
              >
                Publish
              </button>
            ) : null}
            {!teach && inReview ? (
              <Link className="eh8s-btn primary" to={`/owner/courses/review/${id}`}>
                Review
              </Link>
            ) : null}
            {canSubmit ? (
              <button
                type="button"
                className="eh8s-btn primary"
                disabled={busy}
                onClick={() => act(() => actions.submit(), "Could not submit the course.")}
              >
                Submit for review
              </button>
            ) : null}
            {course.status === "published" ? (
              <Link className={teach ? "eh8s-btn" : "eh8s-btn primary"} to={`/academy/courses/${id}`}>
                Preview as student
              </Link>
            ) : !teach ? (
              <Link className="eh8s-btn" to={`/academy/courses/${id}`}>
                Preview as student
              </Link>
            ) : null}
            {editable ? (
              <Link className="eh8s-btn" to={`${root}/${id}/edit`}>
                Course details
              </Link>
            ) : null}
            {!teach && course.status === "published" ? (
              <button
                type="button"
                className="eh8s-btn"
                disabled={busy}
                onClick={() => act(() => actions.setStatus("draft"), "Could not unpublish.")}
              >
                Back to draft
              </button>
            ) : null}
            {!teach && course.status !== "archived" ? (
              <button
                type="button"
                className="eh8s-btn"
                disabled={busy}
                onClick={() => act(() => actions.setStatus("archive"), "Could not archive.")}
              >
                Archive
              </button>
            ) : null}
          </div>
          {sections.map(({ section, lessons }, sIndex) => (
            <div key={section.id} className="eh8s-outline">
              <h2>
                {sIndex + 1}.{" "}
                {editable ? <Link to={`${root}/${id}/sections/${section.id}`}>{section.title}</Link> : section.title}
                {section.isActive ? "" : " (hidden)"}
                {editable ? (
                  <span className="eh8s-outline-meta">
                    <button
                      type="button"
                      className="eh8s-btn-mini"
                      aria-label={`Move ${section.title} up`}
                      disabled={busy || sIndex === 0}
                      onClick={() => act(() => actions.moveSection(section.id, sIndex), "Could not move the section.")}
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      className="eh8s-btn-mini"
                      aria-label={`Move ${section.title} down`}
                      disabled={busy || sIndex === sections.length - 1}
                      onClick={() => act(() => actions.moveSection(section.id, sIndex + 2), "Could not move the section.")}
                    >
                      ↓
                    </button>
                  </span>
                ) : null}
              </h2>
              <ol>
                {lessons.map((lesson, lIndex) => (
                  <li key={lesson.id} className={lesson.isActive ? "" : "locked"}>
                    {editable ? <Link to={`${root}/${id}/lessons/${lesson.id}`}>{lesson.title}</Link> : lesson.title}
                    <span className="eh8s-outline-meta">
                      {lesson.isFreePreview ? <span className="eh8s-tag">Free preview</span> : null}
                      {lesson.practicePrompt ? <span className="eh8s-tag">Practice</span> : null}
                      {lesson.isActive ? null : <span className="eh8s-tag">Hidden</span>}
                      {PROVIDER_LABEL[lesson.videoProvider]} {formatDuration(lesson.durationSec)}
                      {editable ? (
                        <>
                          <button
                            type="button"
                            className="eh8s-btn-mini"
                            aria-label={`Move ${lesson.title} up`}
                            disabled={busy || lIndex === 0}
                            onClick={() => act(() => actions.moveLesson(lesson.id, lIndex), "Could not move the lesson.")}
                          >
                            ↑
                          </button>
                          <button
                            type="button"
                            className="eh8s-btn-mini"
                            aria-label={`Move ${lesson.title} down`}
                            disabled={busy || lIndex === lessons.length - 1}
                            onClick={() => act(() => actions.moveLesson(lesson.id, lIndex + 2), "Could not move the lesson.")}
                          >
                            ↓
                          </button>
                        </>
                      ) : null}
                    </span>
                  </li>
                ))}
                {editable ? (
                  <li>
                    <Link to={`${root}/${id}/sections/${section.id}/lessons/new`}>+ Add lesson</Link>
                  </li>
                ) : null}
              </ol>
            </div>
          ))}
          {editable ? (
            <form className="eh8s-form eh8s-panel" onSubmit={onAddSection}>
              <label>
                New section title
                <input
                  maxLength={160}
                  placeholder="e.g. Warm-up"
                  value={sectionTitle}
                  onChange={(ev) => setSectionTitle(ev.target.value)}
                />
              </label>
              <button
                type="submit"
                className={sections.length ? "eh8s-btn" : "eh8s-btn primary"}
                disabled={busy || !sectionTitle.trim()}
              >
                Add section
              </button>
            </form>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
