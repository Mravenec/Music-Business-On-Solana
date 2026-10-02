import { Link } from "react-router-dom";
import { PROPOSAL_KINDS } from "./governanceText";

/**
 * Job: pick what kind of change to propose.
 * Primary: one card per kind (Choose).
 * Next: /owner/governance/new/:kind
 * Hidden: every form field until a kind is chosen.
 */
export function GovernanceNewPage() {
  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Governance</p>
        <h1>New proposal</h1>
        <p className="eh8s-lead">Your approval is recorded with the proposal.</p>
      </header>
      <div className="eh8s-band-grid">
        {PROPOSAL_KINDS.map((k) => (
          <article key={k.kind} className="eh8s-band-card">
            <h3>{k.label}</h3>
            <p className="eh8s-band-code">{k.hint}</p>
            <Link className="eh8s-btn primary" to={`/owner/governance/new/${k.kind}`}>
              Choose
            </Link>
          </article>
        ))}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner/governance">
          Back to governance
        </Link>
      </div>
    </section>
  );
}
