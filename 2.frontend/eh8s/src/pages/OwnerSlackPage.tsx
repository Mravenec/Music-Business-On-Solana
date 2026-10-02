import { Link } from "react-router-dom";
import { useSlackStatus } from "../hooks/useSlackStatus";

/**
 * Job: check whether Approve / Reject buttons in Slack answer agent decisions.
 * Primary: Back to decisions.
 * Next: /owner/decisions
 * Hidden: delivery log, webhook / bot secrets, signing secret (never shown).
 */
export function OwnerSlackPage() {
  const s = useSlackStatus();
  const status = s.status;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Decisions</p>
        <h1>Slack approvals</h1>
        <p className="eh8s-lead">
        Yellow and red agent decisions reach Slack with Approve and Reject buttons. A click answers
        the decision once; the message then shows who answered.
      </p>
      </header>
      {!s.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner wallet to see the Slack bridge.</div>
      ) : null}
      {s.loading ? <p className="eh8s-muted-line">Loading the Slack bridge status.</p> : null}
      {s.error ? <div className="eh8s-banner bad">{s.error}</div> : null}
      {status ? (
        <article className="eh8s-score-card">
          <strong>{status.interactive ? "Buttons are live" : "Buttons are off"}</strong>
          <p className="eh8s-muted-line">Delivery mode: {status.mode ?? "skip"}</p>
          {status.interactiveNote ? <p className="eh8s-muted-line">{status.interactiveNote}</p> : null}
          {status.interactionsPath ? (
            <p className="eh8s-muted-line">
              Interactivity Request URL: https://&lt;public host&gt;
              <span className="eh8s-mono">{status.interactionsPath}</span>
            </p>
          ) : null}
        </article>
      ) : null}
      <Link className="eh8s-btn primary eh8s-back" to="/owner/decisions">
        Back to decisions
      </Link>
    </section>
  );
}
