import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { authoringError, useAuthoringScope, useCourseForm } from "../hooks/useCourseAuthoring";

/**
 * Job: name a course and set who it is for.
 * Primary: Create course (new) or Save (edit).
 * Next: {root}/:courseId (owner /owner/courses · instructor /academy/teach/courses)
 * Hidden: sections, lessons, publishing.
 */
export function OwnerCourseFormPage() {
  const { courseId } = useParams();
  const editing = Boolean(courseId);
  const navigate = useNavigate();
  const { scope, root } = useAuthoringScope();
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [instrumentId, setInstrumentId] = useState("");
  const [minLevel, setMinLevel] = useState("0");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const form = useCourseForm(courseId ? Number(courseId) : null, scope);
  const { instruments, levels, course } = form;

  useEffect(() => {
    if (!course) return;
    setTitle(course.title);
    setSummary(course.summary ?? "");
    setInstrumentId(course.instrumentId ? String(course.instrumentId) : "");
    setMinLevel(String(course.minLevel));
  }, [course]);

  useEffect(() => {
    if (form.error) setMsg(form.error);
  }, [form.error]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    const input = {
      title: title.trim(),
      summary: summary.trim() || null,
      instrumentId: instrumentId ? Number(instrumentId) : null,
      minLevel: Number(minLevel),
    };
    try {
      const saved = await form.save(input);
      navigate(`${root}/${saved.id}`);
    } catch (err) {
      setMsg(authoringError(err, "Could not save the course.", scope));
    } finally {
      setBusy(false);
    }
  }

  const levelOptions = levels.length
    ? levels.map((l) => ({ value: l.levelNumber, label: `${l.levelNumber} · ${l.name}` }))
    : [0, 1, 2, 3, 4, 5].map((n) => ({ value: n, label: String(n) }));

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Courses</p>
      <h1>{editing ? "Course details" : "New course"}</h1>
      <p className="eh8s-lead">
        {scope === "teach"
          ? "New courses start as drafts. Students see them after the studio owner approves your submission."
          : "New courses start as drafts. Students see them only after you publish."}
      </p>
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Title
          <input required maxLength={160} value={title} onChange={(ev) => setTitle(ev.target.value)} />
        </label>
        <label>
          What students will learn (optional)
          <textarea maxLength={1000} value={summary} onChange={(ev) => setSummary(ev.target.value)} />
        </label>
        <label>
          Instrument (optional)
          <select value={instrumentId} onChange={(ev) => setInstrumentId(ev.target.value)}>
            <option value="">Any instrument</option>
            {instruments.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Minimum Enigma level to watch paid lessons
          <select value={minLevel} onChange={(ev) => setMinLevel(ev.target.value)}>
            {levelOptions.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={busy}>
          {busy ? "Saving…" : editing ? "Save" : "Create course"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={editing ? `${root}/${courseId}` : root}>
          Cancel
        </Link>
      </div>
    </section>
  );
}
