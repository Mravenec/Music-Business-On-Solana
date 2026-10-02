import { useState } from "react";
import { Link } from "react-router-dom";
import { useRoles } from "../hooks/useRoles";

/**
 * Job: revoke one granted role.
 * Primary: Revoke.
 * Next: back to applications.
 * Hidden: pending queue, agent decisions.
 */
export function OwnerRolesGrantedPage() {
  const roles = useRoles();
  const [msg, setMsg] = useState<string | null>(null);

  async function onRevoke(id: number, role: string) {
    try {
      await roles.revoke(id, "Revoked by owner");
      setMsg(`${role} revoked. That wallet can apply again.`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not revoke");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Roles</p>
        <h1>Granted roles</h1>
        <p className="eh8s-lead">People you already approved. Revoke removes the role.</p>
      </header>
      {msg ? <div className="eh8s-banner ok">{msg}</div> : null}
      {roles.grantedQueue.length === 0 ? (
        <p className="eh8s-empty">No granted roles yet.</p>
      ) : (
        roles.grantedQueue.map((row) => (
          <article key={row.id} className="eh8s-score-card">
            <strong>{row.role}</strong>
            <p className="eh8s-muted-line">{row.walletPubkey}</p>
            <button type="button" className="eh8s-btn" onClick={() => void onRevoke(row.id, row.role)}>
              Revoke
            </button>
          </article>
        ))
      )}
      <Link className="eh8s-btn eh8s-back" to="/owner/roles">
        Back to applications
      </Link>
    </section>
  );
}
