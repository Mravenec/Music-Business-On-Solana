import { NavLink, Outlet, Navigate, useLocation, Link } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useRoles } from "../hooks/useRoles";
import { useHealth } from "../hooks/useHealth";
import { WalletConnectButton as ConnectButton } from "./WalletConnectButton";
import { WalletRail } from "./WalletRail";
import "./shell.css";

type NavItem = {
  to: string;
  label: string;
  end?: boolean;
  roles?: string[];
  ownerOnly?: boolean;
};

/** Connected workspace links — Home is the brand mark, not a nav item. */
const ROLE_LINKS: NavItem[] = [
  { to: "/academy", label: "Academy", roles: ["musician", "student", "instructor"] },
  { to: "/academy/teach", label: "Teach", roles: ["instructor"] },
  { to: "/bands", label: "Bands", roles: ["musician", "instructor"] },
  { to: "/stage-map", label: "Stage", roles: ["musician", "venue", "instructor"] },
  { to: "/catalog", label: "Catalog", roles: ["musician", "venue", "instructor"] },
  { to: "/devnet-pay", label: "Payments", roles: ["musician", "student", "instructor", "venue"] },
  { to: "/owner", label: "Inbox", ownerOnly: true },
];

function visibleLinks(
  isOwner: boolean,
  activeWorkspace: string | null,
  connected: boolean
): NavItem[] {
  if (!connected) return [];
  return ROLE_LINKS.filter((link) => {
    if (link.ownerOnly) return isOwner;
    if (isOwner) return false;
    if (!link.roles) return true;
    return activeWorkspace
      ? link.roles.includes(activeWorkspace.toLowerCase())
      : false;
  });
}

/**
 * EH8S product chrome — ordinary website feel; role nav after connect.
 */
export function AppShell() {
  const { session, loading, error, geoError } = useSession();
  const {
    memberships,
    activeWorkspace,
    setActiveWorkspace,
    loading: rolesLoading,
  } = useRoles();
  const health = useHealth();
  const location = useLocation();
  const role = session?.account?.role?.toLowerCase();
  const isOwner = Boolean(session?.platformOwner || role === "owner");
  const connected = Boolean(session?.account);
  const links = visibleLinks(isOwner, activeWorkspace, connected);
  const approvedRoles = memberships.map((m) => m.role.toLowerCase());

  const publicPath =
    location.pathname === "/" ||
    location.pathname === "/status" ||
    location.pathname === "/health";
  if (!loading && !connected && !publicPath) {
    return <Navigate to="/" replace />;
  }

  const ownerOnlyPaths = ["/owner", "/agent-events", "/solana", "/anchor"];
  if (
    connected &&
    !isOwner &&
    ownerOnlyPaths.some((p) => location.pathname.startsWith(p))
  ) {
    return <Navigate to="/" replace />;
  }

  const roleGated = ROLE_LINKS.find(
    (l) =>
      !l.ownerOnly &&
      location.pathname.startsWith(l.to) &&
      l.roles &&
      l.roles.length > 0
  );
  if (
    connected &&
    !isOwner &&
    roleGated?.roles &&
    (!activeWorkspace || !roleGated.roles.includes(activeWorkspace))
  ) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className={`eh8s-shell${connected ? "" : " eh8s-shell-public"}`}>
      <header className="eh8s-top">
        <Link to="/" className="eh8s-brand" aria-label="EH8S home">
          <img className="eh8s-logo" src="/eh8s-mark.png" alt="" />
          <span className="eh8s-brand-copy">
            <span className="eh8s-mark">EH8S</span>
            <span className="eh8s-tag">Enigma H8 Studios</span>
          </span>
        </Link>
        {links.length > 0 ? (
          <nav className="eh8s-nav" aria-label="Primary">
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  isActive ? "eh8s-nav-link active" : "eh8s-nav-link"
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
        ) : (
          <div className="eh8s-nav-spacer" aria-hidden="true" />
        )}
        <div className="eh8s-session">
          <span
            className={health.data ? "eh8s-chip ok" : "eh8s-chip bad"}
            title={
              health.error ||
              (health.data
                ? `${health.data.product ?? "EH8S"} is reachable`
                : "Service unreachable")
            }
          >
            {health.loading ? "…" : health.data ? "Studio online" : "Studio offline"}
          </span>
          <ConnectButton />
        </div>
      </header>
      <div className="eh8s-identity">
        {loading || rolesLoading ? <span>Opening your studio…</span> : null}
        {error ? <span className="warn">Could not sign in: {error}</span> : null}
        {geoError ? (
          <span className="warn">Location not saved: {geoError}</span>
        ) : null}
        {session?.account ? (
          <div className="eh8s-identity-pills">
            <span className="eh8s-pill">
              {isOwner ? "Studio owner" : session.account.displayName}
            </span>
            {isOwner ? (
              <span className="eh8s-pill">
                Fee{" "}
                {session.protocolFeeBps != null
                  ? `${(session.protocolFeeBps / 100).toFixed(1)}%`
                  : "—"}
              </span>
            ) : null}
            {!isOwner && approvedRoles.length === 0 ? (
              <span className="eh8s-pill">No role yet</span>
            ) : null}
            {session.account.lastLat != null && session.account.lastLng != null ? (
              <span className="eh8s-pill">
                Near {Number(session.account.lastLat).toFixed(2)},{" "}
                {Number(session.account.lastLng).toFixed(2)}
              </span>
            ) : null}
            {!isOwner && approvedRoles.length > 0 ? (
              <label className="eh8s-workspace eh8s-pill">
                <span className="eh8s-sr-only">Active workspace</span>
                <select
                  value={activeWorkspace ?? ""}
                  onChange={(e) => setActiveWorkspace(e.target.value)}
                  aria-label="Active workspace"
                >
                  {approvedRoles.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
          </div>
        ) : (
          <span>Connect a wallet to enter.</span>
        )}
      </div>
      <div className={connected && !publicPath ? "eh8s-stage" : undefined}>
        <main className="eh8s-main">
          {publicPath || connected ? (
            <Outlet />
          ) : (
            <p className="eh8s-muted-line">Opening your studio…</p>
          )}
        </main>
        {connected && !publicPath ? <WalletRail /> : null}
      </div>
      <footer className="eh8s-footer">
        <span>© EH8S · Enigma H8 Studios</span>
        <Link to="/status">Studio availability</Link>
      </footer>
    </div>
  );
}
