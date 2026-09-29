import { Link } from "react-router-dom";
import { useVaultQueue } from "../hooks/useVaultQueue";

/**
 * Job: pick the next confirmed escrow to settle.
 * Primary: Review settlement.
 * Next: /agents/vault/bookings/:bookingId
 * Hidden: fee / pool split, member accounts, instruction bytes.
 */
export function VaultQueuePage() {
  const q = useVaultQueue();
  const role = q.queue?.signerRole === "owner" ? "owner" : "VAULT agent";

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">VAULT</p>
        <h1>Escrows to settle</h1>
        <p className="eh8s-lead">
          After the show, the owner or the VAULT agent releases the escrow: expenses to the venue,
          fee to the treasury, the pool to the band.
        </p>
      </header>
      {!q.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or VAULT agent wallet.</div>
      ) : null}
      {q.loading ? <p className="eh8s-muted-line">Loading escrows…</p> : null}
      {q.error ? <div className="eh8s-banner bad">{q.error}</div> : null}
      {q.queue ? <p className="eh8s-muted-line">Signing as {role}.</p> : null}
      <div className="eh8s-band-grid">
        {(q.queue?.confirmedBookings ?? []).map((b) => (
          <article key={b.id} className="eh8s-band-card">
            <h3>Show {b.showDate}</h3>
            <p className="eh8s-band-code">
              Venue #{b.venueId} · band #{b.bandId} · {b.grossUsdc} USDC in escrow
            </p>
            <Link className="eh8s-btn primary" to={`/agents/vault/bookings/${b.id}`}>
              Review settlement
            </Link>
          </article>
        ))}
        {q.queue && !q.queue.confirmedBookings.length ? (
          <p className="eh8s-empty">No confirmed escrow is waiting.</p>
        ) : null}
      </div>
    </section>
  );
}
