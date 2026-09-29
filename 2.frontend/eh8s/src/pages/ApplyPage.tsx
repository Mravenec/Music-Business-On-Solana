import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useRoles } from "../hooks/useRoles";

/**
 * One job: apply for a studio role.
 * Primary: Apply on the chosen role.
 * Next: home after pending; destinations stay locked.
 * Hidden: Academy, bands, catalog, payments, owner tools.
 */
export function ApplyPage() {
  const { session } = useSession();
  const {
    memberships,
    myApplications,
    applyableRoles,
    apply,
    loading: rolesLoading,
    error: rolesError,
  } = useRoles();
  const [applyMsg, setApplyMsg] = useState<string | null>(null);
  const [applying, setApplying] = useState<string | null>(null);

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  const isOwner =
    session.platformOwner || session.account.role?.toLowerCase() === "owner";
  if (isOwner) {
    return <Navigate to="/" replace />;
  }

  const approved = new Set(memberships.map((m) => m.role.toLowerCase()));
  const pending = new Set(
    myApplications
      .filter((a) => a.status === "pending")
      .map((a) => a.role.toLowerCase())
  );

  async function onApply(role: string) {
    setApplying(role);
    setApplyMsg(null);
    try {
      await apply(role);
      setApplyMsg(`Applied for ${role}. Waiting for owner approval.`);
    } catch (err) {
      setApplyMsg(err instanceof Error ? err.message : "Could not apply");
    } finally {
      setApplying(null);
    }
  }

  return (
    <section className="eh8s-role-home">
      <p className="eh8s-kicker">Roles</p>
      <h1>Apply for a role</h1>
      <p className="eh8s-lead">
        Pick one role. Owner approval unlocks that workspace. Pending and
        rejected roles stay locked.
      </p>
      {rolesError ? <div className="eh8s-banner bad">{rolesError}</div> : null}
      {applyMsg ? <div className="eh8s-banner ok">{applyMsg}</div> : null}
      {applyableRoles.length === 0 ? (
        <p className="eh8s-empty">No roles are open to apply for right now.</p>
      ) : (
        <div className="eh8s-role-grid">
          {applyableRoles.map((role) => {
            const has = approved.has(role);
            const wait = pending.has(role);
            return (
              <article key={role} className="eh8s-role-card">
                <strong>{role}</strong>
                <span className="eh8s-muted-line">
                  {has
                    ? "Approved"
                    : wait
                      ? "Waiting for owner review"
                      : "Not applied"}
                </span>
                <button
                  type="button"
                  className="eh8s-btn primary"
                  disabled={has || wait || rolesLoading || applying === role}
                  onClick={() => void onApply(role)}
                >
                  {has
                    ? "Granted"
                    : wait
                      ? "Pending"
                      : applying === role
                        ? "…"
                        : "Apply"}
                </button>
              </article>
            );
          })}
        </div>
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/">
          Back home
        </Link>
      </div>
    </section>
  );
}
