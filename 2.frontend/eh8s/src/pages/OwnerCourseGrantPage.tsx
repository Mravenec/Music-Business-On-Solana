import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCourseGrantActions, useOwnerCourses } from "../hooks/useCourseAuthoring";
import { statusText } from "../hooks/statusText";

/**
 * Job: open paid lessons for one wallet.
 * Primary: Grant access.
 * Next: /owner/course-access
 * Hidden: existing grants, plans, levels.
 */
export function OwnerCourseGrantPage() {
  const navigate = useNavigate();
  const courses = useOwnerCourses();
  const grants = useCourseGrantActions();
  const [wallet, setWallet] = useState("");
  const [courseId, setCourseId] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await grants.grant({
        walletPubkey: wallet.trim(),
        courseId: courseId ? Number(courseId) : null,
        expiresAt: expiresAt ? `${expiresAt}T23:59:59` : null,
        note: note.trim() || null,
      });
      navigate("/owner/course-access");
    } catch (err) {
      setMsg(
        statusText(
          err,
          {
            400: "Use a Solana wallet address and a future end date.",
            403: "Only the platform owner can grant course access.",
          },
          "Could not grant access."
        )
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Course access</p>
      <h1>Grant access</h1>
      <p className="eh8s-lead">Granting the same wallet again updates its note and end date.</p>
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Student wallet
          <input
            required
            minLength={32}
            maxLength={44}
            pattern="[1-9A-HJ-NP-Za-km-z]{32,44}"
            className="eh8s-mono"
            value={wallet}
            onChange={(ev) => setWallet(ev.target.value)}
          />
        </label>
        <label>
          Course
          <select value={courseId} onChange={(ev) => setCourseId(ev.target.value)}>
            <option value="">All courses</option>
            {courses.rows.map(({ course }) => (
              <option key={course.id} value={course.id}>
                {course.title} ({course.status})
              </option>
            ))}
          </select>
        </label>
        <label>
          Until (optional)
          <input type="date" value={expiresAt} onChange={(ev) => setExpiresAt(ev.target.value)} />
        </label>
        <label>
          Note (optional)
          <input maxLength={200} placeholder="Scholarship, guest teacher…" value={note} onChange={(ev) => setNote(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={busy}>
          {busy ? "Granting…" : "Grant access"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/owner/course-access">
          Cancel
        </Link>
      </div>
    </section>
  );
}
