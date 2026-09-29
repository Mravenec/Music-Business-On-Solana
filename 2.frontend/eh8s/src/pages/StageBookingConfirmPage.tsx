import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useStageQueue } from "../hooks/useStageQueue";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";
import type { ConfirmContractBuild } from "../services/venueBookingService";

/**
 * Job: read one booking contract and pin its SHA-256 on-chain.
 * Primary: Confirm and pin contract.
 * Next: back to /agents/stage
 * Hidden: VenueAccessToken PDA, AgentAuthority account, instruction bytes.
 */
export function StageBookingConfirmPage() {
  const { bookingId = "" } = useParams();
  const q = useStageQueue();
  const booking = q.queue?.proposedBookings.find((b) => String(b.id) === bookingId) ?? null;
  const [preview, setPreview] = useState<ConfirmContractBuild | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const { previewContract } = q;

  useEffect(() => {
    if (!booking) return;
    previewContract(booking.id)
      .then(setPreview)
      .catch((err) =>
        setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not load the contract" }),
      );
  }, [booking, previewContract]);

  async function onConfirm() {
    if (!booking) return;
    setMsg(null);
    try {
      const sig = await q.confirmContract(booking.id);
      setMsg({ ok: true, text: "Contract pinned on-chain. The escrow waits for the VAULT settlement.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not confirm" });
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Booking contract</p>
      <h1>{booking ? `Show ${booking.showDate}` : "Booking"}</h1>
      {!q.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or STAGE agent wallet.</div>
      ) : null}
      {q.loading ? <p className="eh8s-muted-line">Loading booking…</p> : null}
      {q.error ? <div className="eh8s-banner bad">{q.error}</div> : null}
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
      {booking && preview ? (
        <div className="eh8s-panel">
          <pre className="eh8s-contract">{preview.contractText}</pre>
          <p className="eh8s-muted-line">SHA-256 {preview.contractHashHex.slice(0, 16)}…</p>
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={q.busy}
            onClick={() => void onConfirm()}
          >
            {q.busy ? "Waiting for wallet…" : "Confirm and pin contract"}
          </button>
        </div>
      ) : null}
      {q.queue && !booking && !msg?.ok ? (
        <p className="eh8s-empty">This booking is not waiting for a contract.</p>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/agents/stage">
          Back to queue
        </Link>
      </div>
    </section>
  );
}
