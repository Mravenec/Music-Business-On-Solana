import { Link, Navigate } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useStudioPower } from "../hooks/useStudioPower";

function shortWallet(wallet: string) {
  if (wallet.length < 12) return wallet;
  return `${wallet.slice(0, 4)}..${wallet.slice(-4)}`;
}

/**
 * Job: pick which studio power the principal will sign next.
 * Primary: Grant studio admin / Set partner share.
 * Next: grant, revoke, or partner-share screens.
 * Hidden: instruction bytes, PDAs, MariaDB partner form, treasury.
 */
export function StudioPowerPage() {
  const { session, loading: sessionLoading } = useSession();
  const power = useStudioPower();
  const principal = Boolean(
    session?.platformOwner || session?.account?.role?.toLowerCase() === "owner",
  );

  if (sessionLoading) {
    return <p className="eh8s-muted-line">Opening studio power…</p>;
  }
  if (!session?.account) {
    return <Navigate to="/" replace />;
  }
  if (!principal) {
    return (
      <section className="eh8s-page">
        <header className="eh8s-page-hero">
          <p className="eh8s-kicker">Studio power</p>
          <h1>Principal wallet only</h1>
          <p className="eh8s-lead">
            Only the principal wallet can grant studio admin or set partner shares on-chain.
          </p>
        </header>
        <div className="eh8s-cta-row">
          <Link className="eh8s-btn eh8s-back" to="/owner">
            Back to inbox
          </Link>
          <Link className="eh8s-btn" to="/studio-ledger">
            Partner books
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio</p>
        <h1>Studio power</h1>
        <p className="eh8s-lead">
          Grant or revoke studio admin and set partner shares with a DevNet wallet signature. The
          books only display what the chain confirmed.
        </p>
      </header>
      {!power.walletReady ? (
        <div className="eh8s-banner bad">Connect the principal wallet to sign.</div>
      ) : null}
      {power.loading ? <p className="eh8s-muted-line">Loading studio power…</p> : null}
      {power.error ? <div className="eh8s-banner bad">{power.error}</div> : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/owner/studio-power/grant">
          Grant studio admin
        </Link>
        <Link className="eh8s-btn" to="/owner/studio-power/partner-share">
          Set partner share
        </Link>
      </div>
      <article className="eh8s-band-card eh8s-plan-includes">
        <h2>Studio admins</h2>
        {power.activeAdmins.length === 0 ? (
          <p className="eh8s-empty">No active studio admin grants on-chain yet.</p>
        ) : (
          <ul>
            {power.activeAdmins.map((row) => (
              <li key={row.id}>
                {shortWallet(row.walletPubkey)} · studio_admin{" "}
                <Link className="eh8s-btn" to={`/owner/studio-power/revoke?wallet=${encodeURIComponent(row.walletPubkey)}`}>
                  Revoke
                </Link>
              </li>
            ))}
          </ul>
        )}
      </article>
      <article className="eh8s-band-card eh8s-plan-includes">
        <h2>Partner shares</h2>
        {power.livePartnerShares.length === 0 ? (
          <p className="eh8s-empty">No live partner shares on-chain yet.</p>
        ) : (
          <ul>
            {power.livePartnerShares.map((row) => (
              <li key={row.id}>
                {shortWallet(row.walletPubkey)} · {(row.shareBps / 100).toFixed(2)}%{" "}
                <Link
                  className="eh8s-btn"
                  to={`/owner/studio-power/partner-share?wallet=${encodeURIComponent(row.walletPubkey)}&bps=${row.shareBps}`}
                >
                  Change
                </Link>
              </li>
            ))}
          </ul>
        )}
      </article>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/studio-ledger">
          Partner books
        </Link>
        <Link className="eh8s-btn eh8s-back" to="/owner">
          Back to inbox
        </Link>
      </div>
    </section>
  );
}
