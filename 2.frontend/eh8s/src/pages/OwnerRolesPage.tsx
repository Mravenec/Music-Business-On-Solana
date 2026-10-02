import { useState } from "react";
import { Link } from "react-router-dom";
import { useRoles } from "../hooks/useRoles";

/**
 * Job: approve or reject one role application.
 * Primary: Approve.
 * Next: next application / inbox.
 * Hidden: agent decisions, Slack, status editor.
 */
export function OwnerRolesPage() {
  const roles = useRoles();
  const [msg, setMsg] = useState<string | null>(null);
  const [reason, setReason] = useState("Does not meet studio criteria");

  async function onApprove(id: number) {
    try {
      await roles.approve(id, "Approved");
      setMsg("Application approved.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not approve");
    }
  }

  async function onReject(id: number) {
    try {
      await roles.reject(id, reason);
      setMsg("Application rejected.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not reject");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Roles</p>
        <h1>Role applications</h1>
        <p className="eh8s-lead">Approve or reject one request at a time.</p>
      </header>
      {msg ? <div className="eh8s-banner ok">{msg}</div> : null}
      {roles.pendingQueue.length === 0 ? (
        <p className="eh8s-empty">No applications waiting.</p>
      ) : (
        roles.pendingQueue.map((row) => (
          <article key={row.id} className="eh8s-score-card">
            <strong>{row.role}</strong>
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
            <label>
              Reject reason
              <input value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
          </article>
        ))
      )}
      <Link className="eh8s-btn" to="/owner/roles/granted">
        Granted roles
      </Link>
      <Link className="eh8s-btn" to="/owner">
        Back to inbox
      </Link>
    </section>
  );
}
