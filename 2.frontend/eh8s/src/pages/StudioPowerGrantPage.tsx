import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useStudioPower } from "../hooks/useStudioPower";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: grant studio_admin to one wallet with a principal signature.
 * Primary: Sign grant.
 * Next: back to /owner/studio-power
 * Hidden: revoke form, partner share form, treasury, instruction bytes.
 */
export function StudioPowerGrantPage() {
  const { session, loading: sessionLoading } = useSession();
  const power = useStudioPower();
  const [wallet, setWallet] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const principal = Boolean(
    session?.platformOwner || session?.account?.role?.toLowerCase() === "owner",
  );
  const walletOk = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(wallet.trim());

  if (sessionLoading) {
    return <p className="eh8s-muted-line">Opening grant…</p>;
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
      const sig = await power.grantAdmin(wallet.trim());
      setMsg({ ok: true, text: "Studio admin granted on DevNet.", sig });
      setWallet("");
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not grant" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio power</p>
        <h1>Grant studio admin</h1>
        <p className="eh8s-lead">
          Sign grant_studio_role so this wallet can open the inbox and read partner books. They
          cannot set partner shares or open the treasury.
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
            placeholder="Base58 wallet to grant studio_admin"
          />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={!walletOk || power.busy}>
          {power.busy ? "Waiting for wallet…" : "Sign grant"}
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
