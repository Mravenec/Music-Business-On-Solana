import { useState } from "react";
import { Link } from "react-router-dom";
import { useGovernance } from "../hooks/useGovernance";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";
import { parseWallets, WALLET_RE } from "./governanceText";

/**
 * Job: turn on governance with a signer set and a threshold (owner wallet signs once).
 * Primary: Sign setup.
 * Next: /owner/governance
 * Hidden: governance PDA, instruction bytes, epoch.
 */
export function GovernanceSetupPage() {
  const g = useGovernance();
  const [text, setText] = useState("");
  const [threshold, setThreshold] = useState(2);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const wallets = parseWallets(text);
  const max = g.view?.maxSigners ?? 5;
  const valid =
    wallets.length >= 1 &&
    wallets.length <= max &&
    wallets.every((w) => WALLET_RE.test(w)) &&
    threshold >= 1 &&
    threshold <= wallets.length;

  async function onSign() {
    setMsg(null);
    try {
      const sig = await g.init(wallets, threshold);
      setMsg({ ok: true, text: `Governance is on: ${threshold} of ${wallets.length} signers.`, sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not set up governance" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Governance</p>
        <h1>Set up signers</h1>
        <p className="eh8s-lead">
        List up to {max} wallets and how many must approve. After this, the owner wallet alone can no
        longer withdraw or grant agent powers.
      </p>
      </header>
      {!g.walletReady ? <div className="eh8s-banner bad">Connect the owner wallet to sign.</div> : null}
      {g.error ? <div className="eh8s-banner bad">{g.error}</div> : null}
      {g.view?.initialized ? (
        <div className="eh8s-banner ok">Governance is already on.</div>
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
      {g.view && !g.view.initialized ? (
        <form
          className="eh8s-form eh8s-panel"
          onSubmit={(ev) => {
            ev.preventDefault();
            void onSign();
          }}
        >
          <label>
            Signer wallets (one per line)
            <textarea
              rows={5}
              value={text}
              onChange={(ev) => setText(ev.target.value)}
              placeholder="Base58 wallet per line"
            />
          </label>
          <label>
            Approvals needed
            <input
              type="number"
              min={1}
              max={Math.max(1, wallets.length)}
              value={threshold}
              onChange={(ev) => setThreshold(Number(ev.target.value))}
            />
          </label>
          <p className="eh8s-muted-line">
            {wallets.length} wallet{wallets.length === 1 ? "" : "s"} listed.
          </p>
          <button type="submit" className="eh8s-btn primary" disabled={!valid || g.busy}>
            {g.busy ? "Waiting for wallet…" : "Sign setup"}
          </button>
        </form>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/owner/governance">
          Back to governance
        </Link>
      </div>
    </section>
  );
}
