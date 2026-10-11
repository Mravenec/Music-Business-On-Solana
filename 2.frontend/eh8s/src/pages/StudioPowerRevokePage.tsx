import { useMemo, useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useStudioPower } from "../hooks/useStudioPower";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: revoke studio_admin from one wallet with a principal signature.
 * Primary: Sign revoke.
 * Next: back to /owner/studio-power
 * Hidden: grant form, partner share form, treasury.
 */
export function StudioPowerRevokePage() {
  const { session, loading: sessionLoading } = useSession();
  const power = useStudioPower();
  const [params] = useSearchParams();
  const initial = useMemo(() => params.get("wallet") ?? "", [params]);
  const [wallet, setWallet] = useState(initial);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const principal = Boolean(
    session?.platformOwner || session?.account?.role?.toLowerCase() === "owner",
  );
  const walletOk = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(wallet.trim());

  if (sessionLoading) {
    return <p className="eh8s-muted-line">Opening revoke…</p>;
  }
  if (!session?.account) {
    return <Navigate to="/" replace />;
  }
  if (!principal) {
    return <Navigate to="/owner/studio-power" replace />;
  }

  async function onSign() {
    setMsg(null);
    try {
      const sig = await power.revokeAdmin(wallet.trim());
      setMsg({ ok: true, text: "Studio admin revoked on DevNet.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not revoke" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio power</p>
        <h1>Revoke studio admin</h1>
        <p className="eh8s-lead">
          Sign revoke_studio_role so this wallet loses inbox studio-admin access. Partner books
          stay readable only while a live grant remains.
        </p>
      </header>
      {!power.walletReady ? (
        <div className="eh8s-banner bad">Connect the principal wallet to sign.</div>
      ) : null}
      {msg ? (
        <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>
          {msg.text}{" "}
          {msg.sig ? (
            <a href={explorerTxUrl(msg.sig)} target="_blank" rel="noreferrer">
              View transaction
            </a>
          ) : null}
        </div>
      ) : null}
      <form
        className="eh8s-form eh8s-panel"
        onSubmit={(ev) => {
          ev.preventDefault();
          void onSign();
        }}
      >
        <label>
          Admin wallet
          <input
            value={wallet}
            onChange={(ev) => setWallet(ev.target.value)}
            spellCheck={false}
            placeholder="Base58 wallet to revoke"
          />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={!walletOk || power.busy}>
          {power.busy ? "Waiting for wallet…" : "Sign revoke"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner/studio-power">
          Back to studio power
        </Link>
      </div>
    </section>
  );
}
