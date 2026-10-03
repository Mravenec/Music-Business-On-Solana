import { useWallet } from "@solana/wallet-adapter-react";
import { Link } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useRoles } from "../hooks/useRoles";
import { usePlatformConfig } from "../hooks/usePlatformConfig";

type HubCta = {
  primaryTo: string;
  primaryLabel: string;
  moreHint: string;
};

function hubForRole(isOwner: boolean, workspace: string | null): HubCta {
  if (isOwner) {
    return {
      primaryTo: "/owner",
      primaryLabel: "Open inbox",
      moreHint: "Approvals and studio tools",
    };
  }
  switch (workspace) {
    case "venue":
      return {
        primaryTo: "/stage-map",
        primaryLabel: "Open stage",
        moreHint: "Catalog and payments",
      };
    case "instructor":
    case "student":
    case "musician":
      return {
        primaryTo: "/academy",
        primaryLabel: "Open academy",
        moreHint: "Bands, stage, catalog, payments",
      };
    default:
      return {
        primaryTo: "/apply",
        primaryLabel: "Apply for a role",
        moreHint: "Destinations unlock after approval",
      };
  }
}

/**
 * Public hub when disconnected; one-job role home when connected.
 * Job: enter the studio or pick the next destination.
 * Primary: Connect wallet (header) or one role CTA.
 * Next: /apply, /more, or the role product family.
 * Hidden: other product consoles until that click.
 */
export function HomePage() {
  const { session } = useSession();
  const { activeWorkspace } = useRoles();
  const { connected } = useWallet();
  const platform = usePlatformConfig();

  if (session?.account) {
    const principal =
      session.platformOwner || session.account.role?.toLowerCase() === "owner";
    const isOwner = principal || Boolean(session.studioAdmin);
    const hub = hubForRole(isOwner, activeWorkspace);

    return (
      <section className="eh8s-role-home">
        <p className="eh8s-kicker">
          {principal
            ? "Studio owner"
            : session.studioAdmin
              ? "Studio admin"
              : activeWorkspace
                ? `Your ${activeWorkspace} home`
                : "Waiting for a role"}
        </p>
        <h1>
          {isOwner
            ? "What needs you"
            : `Welcome, ${session.account.displayName}`}
        </h1>
        <p className="eh8s-lead">
          {principal
            ? "Review the next approval. Other studio tools stay one click away."
            : session.studioAdmin
              ? "Review applications and the partner books. The treasury stays with the principal wallet."
              : activeWorkspace
                ? "One next step for this role. Open More for the rest."
                : "Apply for a role. Destinations stay hidden until the owner approves."}
        </p>
        <div className="eh8s-cta-row">
          <Link className="eh8s-btn primary" to={hub.primaryTo}>
            {hub.primaryLabel}
          </Link>
          {hub.primaryTo !== "/apply" ? (
            <Link className="eh8s-btn" to="/more">
              More
            </Link>
          ) : null}
        </div>
        <p className="eh8s-muted-line">{hub.moreHint}</p>
        {platform && isOwner ? (
          <p className="eh8s-muted-line">
            Platform fee on eligible activity: {platform.protocolFeePercent}%.
          </p>
        ) : null}
      </section>
    );
  }

  return (
    <section className="eh8s-landing">
      <div className="eh8s-hero-stage" aria-hidden="true">
        <div className="eh8s-hero-beam eh8s-hero-beam-a" />
        <div className="eh8s-hero-beam eh8s-hero-beam-b" />
        <div className="eh8s-hero-wave" />
        <div className="eh8s-hero-orb eh8s-hero-orb-a" />
        <img className="eh8s-hero-mark" src="/eh8s-mark.png" alt="" />
        <div className="eh8s-hero-orb eh8s-hero-orb-b" />
      </div>

      <div className="eh8s-hero">
        <div className="eh8s-hero-copy">
          <p className="eh8s-kicker">
            <span className="eh8s-kicker-dot" aria-hidden="true" />
            Music studio on Solana
          </p>
          <h1 className="eh8s-hero-brand">EH8S</h1>
          <p className="eh8s-hero-line">
            Academy, venues, and royalties settle in Solana DevNet USDC — wallet
            signed, never pretend balances.
          </p>
          <p className="eh8s-hero-sub">
            Connect Phantom or Solflare on DevNet. Splits: academy 85/15 to the
            studio, then claim royalties to the musician wallet.
          </p>
          <div className="eh8s-cta-row">
            <span className="eh8s-btn primary ghost">
              {connected ? "Opening your studio…" : "Connect wallet to enter"}
              <span className="eh8s-btn-arrow" aria-hidden="true">
                →
              </span>
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
