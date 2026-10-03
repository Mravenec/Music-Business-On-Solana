import { Link } from "react-router-dom";
import { useOps } from "../hooks/useOps";
import { useRoles } from "../hooks/useRoles";
import { useSession } from "../hooks/useSession";

/**
 * Job: see what needs the owner.
 * Primary: open the next queue.
 * Next: /owner/roles, /owner/decisions, /owner/treasury, /owner/digest, /owner/courses, /owner/agents,
 *   /owner/governance, /agents/levels,
 *   /agents/stage, /agents/vault
 * Hidden: Slack, agent status editor, full history, treasury activity.
 */
export function OwnerPage() {
  const ops = useOps();
  const roles = useRoles();
  const { session } = useSession();
  const principal = Boolean(
    session?.platformOwner || session?.account?.role?.toLowerCase() === "owner"
  );
  const pendingRoles = roles.pendingQueue.length;
  const pendingDecisions = ops.decisions.filter((d) => d.status === "pending").length;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio</p>
        <h1>Inbox</h1>
        <p className="eh8s-lead">Open the next queue. Tools stay one click away.</p>
      </header>
      {ops.loading || roles.loading ? (
        <p className="eh8s-muted-line">Loading inbox…</p>
      ) : null}
      {ops.error || roles.error ? (
        <div className="eh8s-banner bad">Could not load the inbox.</div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/owner/roles">
          Role applications ({pendingRoles})
        </Link>
        <Link className="eh8s-btn" to="/owner/decisions">
          Agent decisions ({pendingDecisions})
        </Link>
        {principal ? (
          <Link className="eh8s-btn" to="/owner/treasury">
            Treasury
          </Link>
        ) : null}
        <Link className="eh8s-btn" to="/owner/digest">
          AI digest
        </Link>
        <Link className="eh8s-btn" to="/owner/courses">
          Courses
        </Link>
        <Link className="eh8s-btn" to="/owner/agents">
          Agent powers
        </Link>
        {principal ? (
          <Link className="eh8s-btn" to="/owner/governance">
            Governance
          </Link>
        ) : null}
        <Link className="eh8s-btn" to="/agents/levels">
          Enigma levels
        </Link>
        <Link className="eh8s-btn" to="/agents/stage">
          Venues and bookings
        </Link>
        <Link className="eh8s-btn" to="/agents/vault">
          Escrows to settle
        </Link>
      </div>
      <p className="eh8s-muted-line">
        Agents, network, and programs live under More from home.
      </p>
    </section>
  );
}
