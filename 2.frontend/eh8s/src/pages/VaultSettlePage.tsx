import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useVaultQueue } from "../hooks/useVaultQueue";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";
import type { SettleBookingBuild } from "../services/venueBookingService";

/**
 * Job: release one confirmed escrow on DevNet.
 * Primary: Settle from escrow.
 * Next: back to /agents/vault (members claim from /stage-map/claim)
 * Hidden: ConcertSettlement PDA, token accounts, instruction bytes.
 */
export function VaultSettlePage() {
  const { bookingId = "" } = useParams();
  const q = useVaultQueue();
  const booking = q.queue?.confirmedBookings.find((b) => String(b.id) === bookingId) ?? null;
  const [preview, setPreview] = useState<SettleBookingBuild | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const { previewSettle } = q;

  useEffect(() => {
    if (!booking) return;
    previewSettle(booking.id)
      .then(setPreview)
      .catch((err) =>
        setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not load the split" }),
      );
  }, [booking, previewSettle]);

  async function onSettle() {
    if (!booking) return;
    setMsg(null);
    try {
      const sig = await q.settle(booking.id);
      setMsg({ ok: true, text: "Settled from escrow. Band members can claim their share.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not settle" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Settlement</p>
        <h1>{booking ? `Show ${booking.showDate}` : "Escrow"}</h1>
      </header>
      {!q.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or VAULT agent wallet.</div>
      ) : null}
      {q.loading ? <p className="eh8s-muted-line">Loading escrow…</p> : null}
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
          <p className="eh8s-lead">
            Escrow {preview.grossUsdc} · expenses {preview.expensesUsdc} · fee {preview.eh8sFeeUsdc}{" "}
            · band pool {preview.bandPoolUsdc} USDC
          </p>
          <ul>
            {preview.memberWallets.map((w, i) => (
              <li key={w}>
                {w.slice(0, 4)}…{w.slice(-4)} → {preview.memberPendingUsdc[i]} USDC pending
              </li>
            ))}
          </ul>
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={q.busy}
            onClick={() => void onSettle()}
          >
            {q.busy ? "Waiting for wallet…" : "Settle from escrow"}
          </button>
        </div>
      ) : null}
      {q.queue && !booking && !msg?.ok ? (
        <p className="eh8s-empty">This escrow is not waiting for a settlement.</p>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/agents/vault">
          Back to escrows
        </Link>
      </div>
    </section>
  );
}
