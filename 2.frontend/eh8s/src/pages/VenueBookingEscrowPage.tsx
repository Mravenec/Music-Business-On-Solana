import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useVenueEscrow } from "../hooks/useVenueEscrow";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: move one booking's escrow forward from the venue side.
 * Primary: Escrow with wallet (not started / cancelled) or Cancel and refund (proposed).
 * Next: back to /stage-map/venues/:venueId/onchain
 * Hidden: VenueAccessToken / escrow PDAs, contract text (STAGE screen), settlement split (VAULT).
 */
export function VenueBookingEscrowPage() {
  const { venueId = "", bookingId = "" } = useParams();
  const v = useVenueEscrow(Number(venueId));
  const booking = v.console?.bookings.find((b) => String(b.id) === bookingId) ?? null;
  const approved = v.console?.venue.listingStatus === "approved";
  const [gross, setGross] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const grossOk = /^\d+(\.\d{1,2})?$/.test(gross.trim()) && Number(gross) > 0;
  const status = booking?.onchainStatus ?? null;

  async function act(run: () => Promise<string>, done: string) {
    setMsg(null);
    try {
      const sig = await run();
      setMsg({ ok: true, text: done, sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not sign" });
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Booking escrow</p>
      <h1>{booking ? `Show ${booking.showDate}` : "Booking"}</h1>
      {v.loading ? <p className="eh8s-muted-line">Loading booking…</p> : null}
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
      {booking && !approved ? (
        <p className="eh8s-empty">The venue listing must be approved before escrowing a show.</p>
      ) : null}
      {booking && approved && (!status || status === "cancelled") ? (
        <form
          className="eh8s-form eh8s-panel"
          onSubmit={(ev) => {
            ev.preventDefault();
            void act(
              () => v.propose(booking.id, gross.trim()),
              "Gross escrowed on DevNet. A STAGE agent confirms the contract next.",
            );
          }}
        >
          <p className="eh8s-lead">
            Band #{booking.bandId}. The gross leaves your USDC account into an escrow only the
            program controls.
          </p>
          <label>
            Gross (USDC)
            <input value={gross} onChange={(ev) => setGross(ev.target.value)} placeholder="1000" />
          </label>
          <button
            type="submit"
            className="eh8s-btn primary"
            disabled={!v.isVenueWallet || !grossOk || v.busy}
          >
            {v.busy ? "Waiting for wallet…" : "Escrow with wallet"}
          </button>
        </form>
      ) : null}
      {booking && status === "proposed" ? (
        <div className="eh8s-panel">
          <p className="eh8s-lead">
            {booking.grossUsdc} USDC in escrow. Waiting for the owner or a STAGE agent to confirm
            the contract. Until then you can cancel for a full refund.
          </p>
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={!v.isVenueWallet || v.busy}
            onClick={() =>
              void act(() => v.cancel(booking.id), "Booking cancelled. The full escrow returned to you.")
            }
          >
            {v.busy ? "Waiting for wallet…" : "Cancel and refund"}
          </button>
        </div>
      ) : null}
      {booking && status === "confirmed" ? (
        <p className="eh8s-lead">
          Contract confirmed on-chain. After the show the owner or a VAULT agent settles
          {` ${booking.grossUsdc} USDC `}from escrow.
        </p>
      ) : null}
      {booking && status === "settled" ? (
        <p className="eh8s-lead">Settled from escrow. Band members can claim their share.</p>
      ) : null}
      {v.console && !booking ? <p className="eh8s-empty">That booking was not found.</p> : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/stage-map/venues/${venueId}/onchain`}>
          Back to venue on-chain
        </Link>
      </div>
    </section>
  );
}
