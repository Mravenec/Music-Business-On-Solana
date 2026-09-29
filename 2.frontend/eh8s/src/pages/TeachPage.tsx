import { Link } from "react-router-dom";
import { useTeachEligibility } from "../hooks/useTeaching";
import type { TeachEligibility } from "../services/teachingService";

/** The one next step for someone who cannot teach yet. */
function nextStep(e: TeachEligibility): { to: string; label: string } {
  const level = e.requirements.find((r) => r.key === "level");
  if (level && !level.met) {
    return e.level == null
      ? { to: "/academy/enroll", label: "Create musician profile" }
      : { to: "/academy/nexus", label: "Raise your level with NEXUS" };
  }
  return { to: "/apply", label: "Apply for the instructor role" };
}

/**
 * Job: know whether you can teach, and what is missing.
 * Primary: My courses (can teach) · Course studio (owner) · the one missing step (enroll, NEXUS or apply).
 * Next: /academy/teach/courses · /owner/courses · /academy/enroll · /academy/nexus · /apply
 * Hidden: course editor, review status (on My courses).
 */
export function TeachPage() {
  const { loading, error, eligibility } = useTeachEligibility();

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Academy</p>
        <h1>Teach</h1>
        <p className="eh8s-lead">
          Musicians who reach Enigma level {eligibility?.requiredLevel ?? 5} and hold an approved instructor role can
          publish video courses. The studio owner reviews every course before students see it.
        </p>
      </header>
      {loading ? <p className="eh8s-muted-line">Checking your teaching status…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {eligibility ? (
        <>
          {eligibility.owner ? (
            <div className="eh8s-banner ok">You own the studio. Your courses publish without review.</div>
          ) : eligibility.canTeach ? (
            <div className="eh8s-banner ok">You can teach.</div>
          ) : null}
          {!eligibility.owner ? (
            <ul className="eh8s-panel">
              {eligibility.requirements.map((r) => (
                <li key={r.key}>
                  {r.met ? "✓" : "○"} <strong>{r.label}</strong> — {r.detail}
                </li>
              ))}
            </ul>
          ) : null}
          <div className="eh8s-cta-row">
            {eligibility.owner ? (
              <Link className="eh8s-btn primary" to="/owner/courses">
                Course studio
              </Link>
            ) : eligibility.canTeach ? (
              <Link className="eh8s-btn primary" to="/academy/teach/courses">
                My courses
              </Link>
            ) : (
              <Link className="eh8s-btn primary" to={nextStep(eligibility).to}>
                {nextStep(eligibility).label}
              </Link>
            )}
          </div>
        </>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/academy">
          Back to Academy
        </Link>
      </div>
    </section>
  );
}
