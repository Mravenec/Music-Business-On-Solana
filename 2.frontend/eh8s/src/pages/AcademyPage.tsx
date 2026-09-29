import { Link } from "react-router-dom";
import { useAcademy } from "../hooks/useAcademy";
import { useSession } from "../hooks/useSession";

/**
 * Job: browse academy plans.
 * Primary: open a plan.
 * Next: /academy/plans/:id (or /academy/courses for the video library, /academy/teach to publish courses)
 * Hidden: enroll, pay, score, on-chain ids, lessons.
 */
export function AcademyPage() {
  const academy = useAcademy();
  const { session } = useSession();
  const musicianId = session?.musicianProfile?.id;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Lessons</p>
        <h1>Academy</h1>
        <p className="eh8s-lead">Pick a plan. Payment and scores live on the next screens.</p>
      </header>
      {academy.loading ? <p className="eh8s-muted-line">Loading plans…</p> : null}
      {academy.error ? (
        <div className="eh8s-banner bad">Could not load academy. Try again shortly.</div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/academy/courses">
          Video courses
        </Link>
        <Link className="eh8s-btn" to="/academy/teach">
          Teach
        </Link>
        {!musicianId ? (
          <Link className="eh8s-btn" to="/academy/enroll">
            Create musician profile
          </Link>
        ) : (
          <>
            <Link className="eh8s-btn" to="/academy/nexus">
              Score Enigma (NEXUS)
            </Link>
            <Link className="eh8s-btn" to="/academy/evaluate">
              Record a score
            </Link>
          </>
        )}
      </div>
      <div className="eh8s-band-grid">
        {academy.plans.map((row) => (
          <article key={row.id} className="eh8s-band-card">
            <div className="eh8s-band-card-top">
              <span className="eh8s-band-id">{row.name}</span>
              <span className="eh8s-badge ok">${row.usdcMonthly} / month</span>
            </div>
            <h3>{row.name}</h3>
            <p className="eh8s-band-code">{row.description}</p>
            <Link className="eh8s-btn primary" to={`/academy/plans/${row.id}`}>
              Open plan
            </Link>
          </article>
        ))}
        {!academy.plans.length && !academy.loading ? (
          <p className="eh8s-empty">No plans are listed yet.</p>
        ) : null}
      </div>
    </section>
  );
}
