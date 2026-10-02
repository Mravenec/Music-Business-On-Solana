import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useVenueEscrow } from "../hooks/useVenueEscrow";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

const STATUS_TEXT: Record<string, string> = {
  pending: "Listed on-chain — waiting for the owner or a STAGE agent to approve it.",
  approved: "Approved on-chain — you can escrow a show below.",
};

/**
 * Job: list this venue on-chain, then pick one booking to escrow.
 * Primary: Register on-chain (until listed); afterwards Open booking.
 * Next: /stage-map/venues/:venueId/bookings/:bookingId
 * Hidden: listing PDA, instruction bytes, escrow accounts.
 */
export function VenueOnchainPage() {
  const { venueId = "" } = useParams();
  const v = useVenueEscrow(Number(venueId));
  const venue = v.console?.venue ?? null;
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  async function onRegister() {
    setMsg(null);
    try {
      const sig = await v.register();
      setMsg({ ok: true, text: "Venue listed on-chain. The owner or a STAGE agent approves it next.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not register" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Venue on-chain</p>
        <h1>{venue?.name ?? "Venue"}</h1>
      </header>
      {v.loading ? <p className="eh8s-muted-line">Loading venue…</p> : null}
      {v.error ? <div className="eh8s-banner bad">{v.error}</div> : null}
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
      {venue && !venue.listingStatus ? (
        <div className="eh8s-panel">
          <p className="eh8s-lead">
            The wallet that signs becomes this venue's wallet. It pays the listing rent and later
            escrows each show.
          </p>
          <p className="eh8s-muted-line">
            {venue.city} · {venue.countryCode} · capacity {venue.capacity}
          </p>
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={!v.walletPubkey || v.busy}
            onClick={() => void onRegister()}
          >
            {v.busy ? "Waiting for wallet…" : "Register on-chain"}
          </button>
          {!v.walletPubkey ? <p className="eh8s-muted-line">Connect the venue wallet first.</p> : null}
        </div>
      ) : null}
      {venue?.listingStatus ? (
        <>
          <p className="eh8s-lead">{STATUS_TEXT[venue.listingStatus]}</p>
          {!v.isVenueWallet ? (
            <p className="eh8s-muted-line">Connect the venue wallet to escrow or cancel a show.</p>
          ) : null}
          <div className="eh8s-band-grid">
            {(v.console?.bookings ?? []).map((b) => (
              <article key={b.id} className="eh8s-band-card">
                <h3>Show {b.showDate}</h3>
                <p className="eh8s-band-code">
                  Band #{b.bandId} · escrow {b.onchainStatus ?? "not started"}
                </p>
                <Link className="eh8s-btn primary" to={`/stage-map/venues/${venueId}/bookings/${b.id}`}>
                  Open booking
                </Link>
              </article>
            ))}
            {v.console && !v.console.bookings.length ? (
              <p className="eh8s-empty">No bookings yet — book a show from the venue page.</p>
            ) : null}
          </div>
        </>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to={`/stage-map/venues/${venueId}`}>
          Back to venue
        </Link>
      </div>
    </section>
  );
}
