import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAgentAuthority } from "../hooks/useAgentAuthority";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: sign which on-chain powers one agent wallet holds.
 * Primary: Sign authorization (or Revoke every power when no box is ticked).
 * Next: back to /owner/agents
 * Hidden: AgentAuthority PDA seeds, instruction bytes, other agents.
 */
export function OwnerAgentAuthorizePage() {
  const { code = "" } = useParams();
  const a = useAgentAuthority();
  const agent = a.roster?.agents.find((row) => row.code === code) ?? null;
  const latest = a.latestFor(code);
  const [wallet, setWallet] = useState("");
  const [mask, setMask] = useState(0);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  useEffect(() => {
    setWallet(latest?.agentWalletPubkey ?? agent?.walletPubkey ?? "");
    setMask(latest?.permissions ?? 0);
  }, [latest, agent]);

  const walletOk = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(wallet.trim());

  async function onSign() {
    setMsg(null);
    try {
      const sig = await a.authorize({
        agentCode: code,
        agentWalletPubkey: wallet.trim(),
        permissions: mask,
      });
      setMsg({
        ok: true,
        text: mask ? `${code} now holds the signed powers.` : `${code} lost every on-chain power.`,
        sig,
      });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not authorize" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Agent powers</p>
        <h1>{agent?.name ?? code}</h1>
        <p className="eh8s-lead">{agent?.roleSummary ?? "Pick the powers this agent wallet may use."}</p>
      </header>
      {!a.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner wallet to sign.</div>
      ) : null}
      {a.loading ? <p className="eh8s-muted-line">Loading agent…</p> : null}
      {a.error ? <div className="eh8s-banner bad">{a.error}</div> : null}
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
      {a.roster && agent ? (
        <form
          className="eh8s-form eh8s-panel"
          onSubmit={(ev) => {
            ev.preventDefault();
            void onSign();
          }}
        >
          <label>
            Agent wallet
            <input
              value={wallet}
              onChange={(ev) => setWallet(ev.target.value)}
              placeholder="Base58 wallet the agent signs with"
            />
          </label>
          {a.roster.permissionBits.map((bit) => (
            <label key={bit.mask} className="eh8s-check">
              <input
                type="checkbox"
                checked={(mask & bit.mask) !== 0}
                onChange={(ev) => setMask(ev.target.checked ? mask | bit.mask : mask & ~bit.mask)}
              />
              {bit.power}
            </label>
          ))}
          <button type="submit" className="eh8s-btn primary" disabled={!walletOk || a.busy}>
            {a.busy ? "Waiting for wallet…" : mask ? "Sign authorization" : "Revoke every power"}
          </button>
        </form>
      ) : null}
      {a.roster && !agent ? <p className="eh8s-empty">Unknown agent {code}.</p> : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner/agents">
          Back to agents
        </Link>
      </div>
    </section>
  );
}
