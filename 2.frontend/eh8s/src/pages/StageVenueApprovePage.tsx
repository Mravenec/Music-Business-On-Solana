import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useStageQueue } from "../hooks/useStageQueue";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: approve one pending venue listing on-chain.
 * Primary: Approve venue.
 * Next: back to /agents/stage
 * Hidden: listing PDA, AgentAuthority account, instruction bytes.
 */
export function StageVenueApprovePage() {
  const { venueId = "" } = useParams();
  const q = useStageQueue();
  const venue = q.queue?.pendingVenues.find((v) => String(v.id) === venueId) ?? null;
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  async function onApprove() {
    if (!venue) return;
    setMsg(null);
    try {
      const sig = await q.approve(venue.id);
      setMsg({ ok: true, text: `${venue.name} is approved. It can now escrow shows.`, sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not approve" });
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Venue approval</p>
      <h1>{venue?.name ?? "Venue"}</h1>
      {!q.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or STAGE agent wallet.</div>
      ) : null}
      {q.loading ? <p className="eh8s-muted-line">Loading venue…</p> : null}
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
      {venue ? (
        <div className="eh8s-panel">
          <p className="eh8s-lead">
            {venue.city} · {venue.countryCode} · capacity {venue.capacity}
          </p>
          <p className="eh8s-muted-line">
            Venue wallet {venue.walletPubkey?.slice(0, 4)}…{venue.walletPubkey?.slice(-4)}
          </p>
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={q.busy}
            onClick={() => void onApprove()}
          >
            {q.busy ? "Waiting for wallet…" : "Approve venue"}
          </button>
        </div>
      ) : null}
      {q.queue && !venue && !msg?.ok ? (
        <p className="eh8s-empty">This venue is not waiting for approval.</p>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/agents/stage">
          Back to queue
        </Link>
      </div>
    </section>
  );
}
