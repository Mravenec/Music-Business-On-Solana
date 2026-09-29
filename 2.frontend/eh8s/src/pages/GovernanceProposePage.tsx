import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useGovernance } from "../hooks/useGovernance";
import { useOps } from "../hooks/useOps";
import type { ProposalInput, ProposalKind } from "../services/governanceService";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";
import { parseWallets, PERMISSION_BITS, PROPOSAL_KINDS, shortKey, WALLET_RE } from "./governanceText";

/**
 * Job: open one proposal of the chosen kind.
 * Primary: Sign proposal.
 * Next: /owner/governance
 * Hidden: proposal PDA, on-chain id, instruction bytes, other kinds' fields.
 */
export function GovernanceProposePage() {
  const { kind: rawKind = "" } = useParams();
  const kind = rawKind as ProposalKind;
  const meta = PROPOSAL_KINDS.find((k) => k.kind === kind);
  const g = useGovernance();
  const ops = useOps();
  const [amount, setAmount] = useState("");
  const [destination, setDestination] = useState("");
  const [agentCode, setAgentCode] = useState("");
  const [agentWallet, setAgentWallet] = useState("");
  const [mask, setMask] = useState(0);
  const [signerText, setSignerText] = useState("");
  const [newThreshold, setNewThreshold] = useState(1);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const newSigners = parseWallets(signerText);

  function input(): ProposalInput | null {
    if (kind === "withdraw") {
      if (!(Number(amount) > 0)) return null;
      return { kind, amountUsdc: amount, destinationWalletPubkey: destination || g.walletPubkey || "" };
    }
    if (kind === "authorize_agent") {
      if (!agentCode || !WALLET_RE.test(agentWallet.trim())) return null;
      return { kind, agentCode, agentWalletPubkey: agentWallet.trim(), permissions: mask };
    }
    if (kind === "update_signers") {
      const ok =
        newSigners.length >= 1 &&
        newSigners.length <= (g.view?.maxSigners ?? 5) &&
        newSigners.every((w) => WALLET_RE.test(w)) &&
        newThreshold >= 1 &&
        newThreshold <= newSigners.length;
      return ok ? { kind, newSigners, newThreshold } : null;
    }
    return null;
  }

  const body = input();

  async function onSign() {
    if (!body) return;
    setMsg(null);
    try {
      const sig = await g.propose(body);
      setMsg({ ok: true, text: "Proposal opened with your approval.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not open the proposal" });
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">New proposal</p>
      <h1>{meta?.label ?? "Unknown kind"}</h1>
      <p className="eh8s-lead">{meta?.hint}</p>
      {!g.walletReady ? <div className="eh8s-banner bad">Connect a signer wallet.</div> : null}
      {g.error ? <div className="eh8s-banner bad">{g.error}</div> : null}
      {g.view && !g.isSigner ? (
        <div className="eh8s-banner bad">Only a governance signer can open proposals.</div>
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
      {meta && g.isSigner ? (
        <form
          className="eh8s-form eh8s-panel"
          onSubmit={(ev) => {
            ev.preventDefault();
            void onSign();
          }}
        >
          {kind === "withdraw" ? (
            <>
              <label>
                Amount (USDC)
                <input value={amount} onChange={(ev) => setAmount(ev.target.value)} inputMode="decimal" />
              </label>
              <label>
                Pay to signer
                <select value={destination} onChange={(ev) => setDestination(ev.target.value)}>
                  <option value="">Me ({shortKey(g.walletPubkey)})</option>
                  {(g.view?.signers ?? [])
                    .filter((s) => s !== g.walletPubkey)
                    .map((s) => (
                      <option key={s} value={s}>
                        {shortKey(s)}
                      </option>
                    ))}
                </select>
              </label>
            </>
          ) : null}
          {kind === "authorize_agent" ? (
            <>
              <label>
                Agent
                <select value={agentCode} onChange={(ev) => setAgentCode(ev.target.value)}>
                  <option value="">Choose an agent</option>
                  {ops.agents.map((a) => (
                    <option key={a.code} value={a.code}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Agent wallet
                <input
                  value={agentWallet}
                  onChange={(ev) => setAgentWallet(ev.target.value)}
                  placeholder="Base58 wallet the agent signs with"
                />
              </label>
              {PERMISSION_BITS.map((bit) => (
                <label key={bit.mask} className="eh8s-check">
                  <input
                    type="checkbox"
                    checked={(mask & bit.mask) !== 0}
                    onChange={(ev) => setMask(ev.target.checked ? mask | bit.mask : mask & ~bit.mask)}
                  />
                  {bit.label}
                </label>
              ))}
            </>
          ) : null}
          {kind === "update_signers" ? (
            <>
              <label>
                New signer wallets (one per line)
                <textarea rows={5} value={signerText} onChange={(ev) => setSignerText(ev.target.value)} />
              </label>
              <label>
                Approvals needed
                <input
                  type="number"
                  min={1}
                  max={Math.max(1, newSigners.length)}
                  value={newThreshold}
                  onChange={(ev) => setNewThreshold(Number(ev.target.value))}
                />
              </label>
              <p className="eh8s-muted-line">Open proposals go stale once this executes.</p>
            </>
          ) : null}
          <button type="submit" className="eh8s-btn primary" disabled={!body || g.busy}>
            {g.busy ? "Waiting for wallet…" : "Sign proposal"}
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
