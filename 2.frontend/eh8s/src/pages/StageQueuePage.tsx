import { Link } from "react-router-dom";
import { useStageQueue } from "../hooks/useStageQueue";

/**
 * Job: pick the next venue listing to approve or booking to confirm.
 * Primary: Review venue / Review contract.
 * Next: /agents/stage/venues/:venueId, /agents/stage/bookings/:bookingId
 * Hidden: listing and access-token PDAs, contract text, instruction bytes.
 */
export function StageQueuePage() {
  const q = useStageQueue();
  const role = q.queue?.signerRole === "owner" ? "owner" : "STAGE agent";

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">STAGE</p>
        <h1>Venues and bookings</h1>
        <p className="eh8s-lead">
          Venues list themselves and escrow each show. The owner or the STAGE agent approves
          listings and confirms contracts on-chain.
        </p>
      </header>
      {!q.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or STAGE agent wallet.</div>
      ) : null}
      {q.loading ? <p className="eh8s-muted-line">Loading queue…</p> : null}
      {q.error ? <div className="eh8s-banner bad">{q.error}</div> : null}
      {q.queue ? <p className="eh8s-muted-line">Signing as {role}.</p> : null}
      {q.queue ? (
        <>
          <h2>Venues waiting for approval ({q.queue.pendingVenues.length})</h2>
          <div className="eh8s-band-grid">
            {q.queue.pendingVenues.map((v) => (
              <article key={v.id} className="eh8s-band-card">
                <h3>{v.name}</h3>
                <p className="eh8s-band-code">
                  {v.city} · {v.countryCode} · capacity {v.capacity}
                </p>
                <Link className="eh8s-btn primary" to={`/agents/stage/venues/${v.id}`}>
                  Review venue
                </Link>
              </article>
            ))}
            {!q.queue.pendingVenues.length ? <p className="eh8s-empty">No venue is waiting.</p> : null}
          </div>
          <h2>Bookings waiting for a contract ({q.queue.proposedBookings.length})</h2>
          <div className="eh8s-band-grid">
            {q.queue.proposedBookings.map((b) => (
              <article key={b.id} className="eh8s-band-card">
                <h3>Show {b.showDate}</h3>
                <p className="eh8s-band-code">
                  Venue #{b.venueId} · band #{b.bandId} · {b.grossUsdc} USDC in escrow
                </p>
                <Link className="eh8s-btn primary" to={`/agents/stage/bookings/${b.id}`}>
                  Review contract
                </Link>
              </article>
            ))}
            {!q.queue.proposedBookings.length ? (
              <p className="eh8s-empty">No booking is waiting.</p>
            ) : null}
          </div>
        </>
      ) : null}
    </section>
  );
}
