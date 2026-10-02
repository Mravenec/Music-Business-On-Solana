import { Link } from "react-router-dom";
import { useOwnerTreasury } from "../hooks/useOwnerTreasury";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: audit what entered and left the treasury.
 * Primary: open the treasury account on Explorer.
 * Next: back to /owner/treasury
 * Hidden: the withdraw form.
 */
export function OwnerTreasuryActivityPage() {
  const t = useOwnerTreasury();
  const inflows = t.treasury?.inflows ?? [];
  const withdrawals = t.treasury?.withdrawals ?? [];

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Treasury</p>
        <h1>Activity</h1>
      </header>
      {!t.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner wallet to open the treasury.</div>
      ) : null}
      {t.loading ? <p className="eh8s-muted-line">Loading activity…</p> : null}
      {t.error ? <div className="eh8s-banner bad">{t.error}</div> : null}
      {t.treasury ? (
        <div className="eh8s-cta-row">
          <a className="eh8s-btn primary" href={t.treasury.explorerUrl} target="_blank" rel="noreferrer">
            Treasury account on Explorer
          </a>
        </div>
      ) : null}
      <h2>Fees received</h2>
      {inflows.length === 0 ? (
        <p className="eh8s-empty">No recorded fee payments yet.</p>
      ) : (
        inflows.map((row) => (
          <article key={`${row.source}-${row.recordId}`} className="eh8s-score-card">
            <strong>
              {row.source} #{row.recordId} · {Number(row.amountUsdc).toFixed(2)} USDC
            </strong>{" "}
            <a href={explorerTxUrl(row.txSignature)} target="_blank" rel="noreferrer">
              View transaction
            </a>
          </article>
        ))
      )}
      <h2>Withdrawals</h2>
      {withdrawals.length === 0 ? (
        <p className="eh8s-empty">No withdrawals yet.</p>
      ) : (
        withdrawals.map((row) => (
          <article key={row.id} className="eh8s-score-card">
            <strong>{Number(row.amountUsdc).toFixed(2)} USDC</strong>{" "}
            <a href={explorerTxUrl(row.txSignature)} target="_blank" rel="noreferrer">
              View transaction
            </a>
          </article>
        ))
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/owner/treasury">
          Back to treasury
        </Link>
      </div>
    </section>
  );
}
