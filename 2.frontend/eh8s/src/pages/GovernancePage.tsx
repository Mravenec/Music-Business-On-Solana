import { Link } from "react-router-dom";
import { useGovernance } from "../hooks/useGovernance";
import { describeProposal } from "./governanceText";

/**
 * Job: see which treasury and agent changes wait for signer approvals.
 * Primary: Set up signers (before init) · Open (next proposal) · New proposal.
 * Next: /owner/governance/setup, /owner/governance/proposals/:id, /owner/governance/new
 * Hidden: governance PDA, epochs, instruction bytes, executed history details.
 */
export function GovernancePage() {
  const g = useGovernance();
  const v = g.view;
  const open = (v?.proposals ?? []).filter((row) => row.proposal.status === "open");
  const closed = (v?.proposals ?? []).filter((row) => row.proposal.status !== "open");

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Governance</p>
        <h1>Signer approvals</h1>
        <p className="eh8s-lead">
          Once governance is on, treasury withdrawals and agent powers need several signers, not one
          wallet.
        </p>
      </header>
      {!g.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or a signer wallet.</div>
      ) : null}
      {g.loading ? <p className="eh8s-muted-line">Loading governance…</p> : null}
      {g.error ? <div className="eh8s-banner bad">{g.error}</div> : null}

      {v && !v.initialized ? (
        <div className="eh8s-panel">
          <p>Governance is off. The owner wallet alone can withdraw and grant agent powers.</p>
          <Link className="eh8s-btn primary" to="/owner/governance/setup">
            Set up signers
          </Link>
        </div>
      ) : null}

      {v?.initialized && v.governance ? (
        <>
          <p className="eh8s-muted-line">
            {v.governance.threshold} of {v.signers.length} signers must approve.{" "}
            {g.isSigner ? "You are a signer." : "You are not a signer."}
          </p>
          <div className="eh8s-band-grid">
            {open.map((row) => (
              <article key={row.proposal.id} className="eh8s-band-card">
                <h3>{describeProposal(row.proposal)}</h3>
                <p className="eh8s-band-code">
                  {row.approvalCount} of {v.governance?.threshold} approvals
                  {row.executable ? " · ready" : ""}
                </p>
                <Link
                  className="eh8s-btn primary"
                  to={`/owner/governance/proposals/${row.proposal.id}`}
                >
                  Open
                </Link>
              </article>
            ))}
            {!open.length ? <p className="eh8s-empty">No proposal is waiting.</p> : null}
          </div>
          {closed.length ? (
            <p className="eh8s-muted-line">
              {closed.filter((r) => r.proposal.status === "executed").length} executed ·{" "}
              {closed.filter((r) => r.proposal.status === "stale").length} stale
            </p>
          ) : null}
          {g.isSigner ? (
            <div className="eh8s-cta-row">
              <Link className="eh8s-btn" to="/owner/governance/new">
                New proposal
              </Link>
            </div>
          ) : null}
        </>
      ) : null}

      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner">
          Back to inbox
        </Link>
      </div>
    </section>
  );
}
