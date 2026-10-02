import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAuthoringScope, useOwnerCourse } from "../hooks/useCourseAuthoring";

/**
 * Job: rename or hide one section.
 * Primary: Save.
 * Next: {root}/:courseId
 * Hidden: lessons of other sections, publishing.
 */
export function OwnerSectionPage() {
  const { courseId = "", sectionId = "" } = useParams();
  const navigate = useNavigate();
  const { scope, root } = useAuthoringScope();
  const { loading, error, outline, run, actions } = useOwnerCourse(Number(courseId), scope);
  const row = outline?.sections.find((s) => s.section.id === Number(sectionId));
  const [title, setTitle] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!row) return;
    setTitle(row.section.title);
    setActive(Boolean(row.section.isActive));
  }, [row]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    const err = await run(() => actions.updateSection(Number(sectionId), { title: title.trim(), isActive: active ? 1 : 0 }), "Could not save the section.");
    setBusy(false);
    if (err) setMsg(err);
    else navigate(`${root}/${courseId}`);
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">
        <Link to={`${root}/${courseId}`}>{outline?.course.title ?? "Course"}</Link>
      </p>
        <h1>Section</h1>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading section…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {!loading && outline && !row ? <div className="eh8s-banner bad">That section is not in this course.</div> : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      {row ? (
        <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
          <label>
            Title
            <input required maxLength={160} value={title} onChange={(ev) => setTitle(ev.target.value)} />
          </label>
          <label className="eh8s-check">
            <input type="checkbox" checked={active} onChange={(ev) => setActive(ev.target.checked)} />
            Visible to students ({row.lessons.length} lessons)
          </label>
          <button type="submit" className="eh8s-btn primary" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </button>
        </form>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`${root}/${courseId}/sections/${sectionId}/lessons/new`}>
          Add lesson here
        </Link>
        <Link className="eh8s-btn" to={`${root}/${courseId}`}>
          Back to course
        </Link>
      </div>
    </section>
  );
}
