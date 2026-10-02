import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useOps } from "../hooks/useOps";

/**
 * Job: approve or reject one agent decision.
 * Primary: Approve.
 * Next: /owner/decisions/answered (who answered, web or Slack) · /owner/slack · inbox.
 * Hidden: answered history, Slack setup, role queue, status editor.
 */
export function OwnerDecisionsPage() {
  const ops = useOps();
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const pending = useMemo(
    () => ops.decisions.filter((d) => d.status === "pending"),
    [ops.decisions]
  );

  async function onApprove(id: number) {
    try {
      await ops.approve(id);
      setMsg({ ok: true, text: "Decision approved." });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not approve" });
    }
  }

  async function onReject(id: number) {
    try {
      await ops.reject(id, "Needs revision");
      setMsg({ ok: true, text: "Decision rejected." });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not reject" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Decisions</p>
        <h1>Agent decisions</h1>
        <p className="eh8s-lead">One approval at a time - here or with the buttons in Slack.</p>
      </header>
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      {pending.length === 0 ? (
        <p className="eh8s-empty">Nothing waiting.</p>
      ) : (
        pending.map((row) => (
          <article key={row.id} className="eh8s-score-card">
            <strong>{row.title ?? `Decision ${row.id}`}</strong>
            <div className="eh8s-cta-row">
              <button
                type="button"
                className="eh8s-btn primary"
                onClick={() => void onApprove(row.id)}
              >
                Approve
              </button>
              <button type="button" className="eh8s-btn" onClick={() => void onReject(row.id)}>
                Reject
              </button>
            </div>
          </article>
        ))
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/owner/decisions/answered">
          Answered decisions
        </Link>
        <Link className="eh8s-btn" to="/owner/slack">
          Slack approvals
        </Link>
        <Link className="eh8s-btn eh8s-back" to="/owner">
          Back to inbox
        </Link>
      </div>
    </section>
  );
}
