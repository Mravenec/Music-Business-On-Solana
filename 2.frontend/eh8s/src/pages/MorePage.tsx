import { Link, Navigate } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useRoles } from "../hooks/useRoles";

type Dest = { to: string; label: string; hint: string };

function destinations(isOwner: boolean, workspace: string | null): Dest[] {
  if (isOwner) {
    return [
      { to: "/owner", label: "Inbox", hint: "Approvals waiting on you" },
      { to: "/agent-events", label: "Agents", hint: "What agents recorded" },
      { to: "/solana", label: "Network", hint: "Wallet and chain status" },
      { to: "/anchor", label: "Programs", hint: "On-chain program list" },
    ];
  }
  const ws = (workspace ?? "").toLowerCase();
  const all: Dest[] = [];
  if (["musician", "student", "instructor"].includes(ws)) {
    all.push({ to: "/academy", label: "Academy", hint: "Plans and lessons" });
  }
  if (["musician", "instructor"].includes(ws)) {
    all.push({ to: "/bands", label: "Bands", hint: "Ensembles and rehearsals" });
  }
  if (["musician", "venue", "instructor"].includes(ws)) {
    all.push({ to: "/stage-map", label: "Stage", hint: "Venues and shows" });
    all.push({ to: "/catalog", label: "Catalog", hint: "Tracks and reach" });
  }
  if (["musician", "student", "instructor", "venue"].includes(ws)) {
    all.push({
      to: "/devnet-pay",
      label: "Payments",
      hint: "Confirmed DevNet signatures",
    });
  }
  all.push({ to: "/apply", label: "Roles", hint: "Apply for another role" });
  all.push({ to: "/agents/levels", label: "Levels", hint: "NEXUS agent wallet only" });
  return all;
}

/**
 * One job: pick a secondary destination for this role.
 * Primary: first listed area.
 * Next: that route family.
 * Hidden: other roles' product areas.
 */
export function MorePage() {
  const { session } = useSession();
  const { activeWorkspace } = useRoles();

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  const isOwner =
    session.platformOwner ||
    session.studioAdmin ||
    session.account.role?.toLowerCase() === "owner";
  const items = destinations(isOwner, activeWorkspace);

  return (
    <section className="eh8s-role-home">
      <p className="eh8s-kicker">More</p>
      <h1>Where next?</h1>
      <p className="eh8s-lead">
        Only destinations for this role. Each one is its own screen.
      </p>
      {items.length === 0 ? (
        <p className="eh8s-empty">
          No extra destinations yet. Apply for a role first.
        </p>
      ) : (
        <div className="eh8s-role-grid">
          {items.map((item, i) => (
            <article key={item.to} className="eh8s-role-card">
              <strong>{item.label}</strong>
              <span className="eh8s-muted-line">{item.hint}</span>
              <Link
                className={i === 0 ? "eh8s-btn primary" : "eh8s-btn"}
                to={item.to}
              >
                Open
              </Link>
            </article>
          ))}
        </div>
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/">
          Back home
        </Link>
      </div>
    </section>
  );
}
