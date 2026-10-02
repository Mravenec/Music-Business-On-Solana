import { useState } from "react";
import { Link } from "react-router-dom";
import { useCourseGrantActions, useCourseGrants, useOwnerCourses } from "../hooks/useCourseAuthoring";
import { statusText } from "../hooks/statusText";

function shortWallet(wallet: string): string {
  return wallet.length > 12 ? `${wallet.slice(0, 4)}…${wallet.slice(-4)}` : wallet;
}

/**
 * Job: see who has free course access and revoke it.
 * Primary: Grant access.
 * Next: /owner/course-access/new
 * Hidden: plan payments and Enigma levels (they open courses on their own).
 */
export function OwnerCourseAccessPage() {
  const { loading, error, grants, reload } = useCourseGrants();
  const courses = useOwnerCourses();
  const grantActions = useCourseGrantActions();
  const [busy, setBusy] = useState<number | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const titles = new Map(courses.rows.map((r) => [r.course.id, r.course.title]));

  async function revoke(id: number) {
    setBusy(id);
    setMsg(null);
    try {
      await grantActions.revoke(id);
      await reload();
    } catch (err) {
      setMsg(statusText(err, { 403: "Only the platform owner can manage course access." }, "Could not revoke."));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Courses</p>
        <h1>Course access</h1>
        <p className="eh8s-lead">
          Scholarships and guests: a grant opens paid lessons for one wallet, without a plan or level.
        </p>
      </header>
      {loading ? <p className="eh8s-muted-line">Loading grants…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/owner/course-access/new">
          Grant access
        </Link>
      </div>
      {grants.length ? (
        <div className="eh8s-table-wrap">
          <table className="eh8s-table">
            <thead>
              <tr>
                <th>Wallet</th>
                <th>Course</th>
                <th>Until</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {grants.map((g) => (
                <tr key={g.id}>
                  <td className="eh8s-mono" title={g.walletPubkey}>
                    {shortWallet(g.walletPubkey)}
                  </td>
                  <td>{g.courseId ? titles.get(g.courseId) ?? `Course #${g.courseId}` : "All courses"}</td>
                  <td>{g.expiresAt ? new Date(g.expiresAt).toLocaleDateString() : "No end date"}</td>
                  <td>
                    <span className={`eh8s-badge ${g.isActive ? "ok" : "muted"}`}>{g.isActive ? "Active" : "Revoked"}</span>
                  </td>
                  <td>
                    {g.isActive ? (
                      <button type="button" className="eh8s-btn-mini" disabled={busy === g.id} onClick={() => revoke(g.id)}>
                        Revoke
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : !loading && !error ? (
        <p className="eh8s-empty">No grants yet.</p>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner/courses">
          Back to courses
        </Link>
      </div>
    </section>
  );
}
