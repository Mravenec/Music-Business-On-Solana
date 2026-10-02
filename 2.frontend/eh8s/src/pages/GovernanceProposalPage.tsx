import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useGovernance } from "../hooks/useGovernance";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";
import { describeProposal, shortKey } from "./governanceText";

/**
 * Job: move one proposal forward.
 * Primary: Approve (signer who has not approved) or Execute (threshold reached).
 * Next: /owner/governance
 * Hidden: proposal PDA, instruction bytes, full approval signatures.
 */
export function GovernanceProposalPage() {
  const { id = "" } = useParams();
  const proposalId = Number(id);
  const g = useGovernance();
  const row = g.proposal(proposalId);
  const threshold = g.view?.governance?.threshold ?? 0;
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const p = row?.proposal;
  const canApprove = Boolean(row && p?.status === "open" && g.isSigner && !row.approvedByMe);
  const canExecute = Boolean(row?.executable);

  async function onAction(kind: "approve" | "execute") {
    setMsg(null);
    try {
      const sig = kind === "approve" ? await g.approve(proposalId) : await g.execute(proposalId);
      setMsg({ ok: true, text: kind === "approve" ? "Approval recorded." : "Proposal executed.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Wallet transaction failed" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Proposal #{p?.onchainProposalId ?? id}</p>
        <h1>{p ? describeProposal(p) : "Proposal"}</h1>
      </header>
      {!g.walletReady ? <div className="eh8s-banner bad">Connect the owner or a signer wallet.</div> : null}
      {g.loading ? <p className="eh8s-muted-line">Loading proposal…</p> : null}
      {g.error ? <div className="eh8s-banner bad">{g.error}</div> : null}
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
      {g.view && !row ? <p className="eh8s-empty">Unknown proposal.</p> : null}
      {row && p ? (
        <div className="eh8s-panel">
          <p>
            Status <strong>{p.status}</strong> · {row.approvalCount} of {threshold} approvals · opened by{" "}
            {shortKey(p.proposerWallet)}
          </p>
          <p className="eh8s-muted-line">
            Approved by {row.approvals.map(shortKey).join(", ") || "nobody yet"}.
          </p>
          {p.status === "stale" ? (
            <p className="eh8s-muted-line">The signer set changed after this proposal. Open a new one.</p>
          ) : null}
          {canExecute ? (
            <button
              type="button"
              className="eh8s-btn primary"
              disabled={g.busy}
              onClick={() => void onAction("execute")}
            >
              {g.busy ? "Waiting for wallet…" : "Execute"}
            </button>
          ) : canApprove ? (
            <button
              type="button"
              className="eh8s-btn primary"
              disabled={g.busy}
              onClick={() => void onAction("approve")}
            >
              {g.busy ? "Waiting for wallet…" : "Approve"}
            </button>
          ) : null}
          {p.executeTxSignature ? (
            <a href={explorerTxUrl(p.executeTxSignature)} target="_blank" rel="noreferrer">
              View execution
            </a>
          ) : null}
        </div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner/governance">
          Back to governance
        </Link>
      </div>
    </section>
  );
}
