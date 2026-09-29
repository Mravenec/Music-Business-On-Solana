import { Link } from "react-router-dom";
import { useDevnetPay } from "../hooks/useDevnetPay";

/**
 * Job: review confirmed DevNet payment signatures.
 * Primary: pay academy.
 * Next: /academy
 */
export function DevnetPayPage() {
  const pay = useDevnetPay();
  const confirmed = pay.markers.filter(
    (row) => row.txSignature && String(row.status).toLowerCase() !== "intended"
  );

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Money</p>
        <h1>Payments</h1>
        <p className="eh8s-lead">
          Confirmed DevNet signatures only. Tuition, settle, and claim each need a wallet.
        </p>
      </header>
      {pay.loading ? <p className="eh8s-muted-line">Loading…</p> : null}
      {pay.error ? (
        <div className="eh8s-banner bad">Could not load payments.</div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/academy">
          Academy tuition
        </Link>
        <Link className="eh8s-btn" to="/stage-map/claim">
          Claim royalties
        </Link>
        <Link className="eh8s-btn" to="/catalog">
          Catalog
        </Link>
      </div>
      {confirmed.length === 0 && !pay.loading ? (
        <p className="eh8s-empty">No confirmed DevNet payments yet.</p>
      ) : (
        <ul>
          {confirmed.map((row) => (
            <li key={row.id}>
              {row.kind} · {row.status}
              {row.amountUsdc != null ? ` · $${row.amountUsdc}` : ""}
              {row.txSignature ? ` · ${row.txSignature.slice(0, 8)}…` : ""}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
