import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useBandVault } from "../hooks/useBandVault";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

function shortWallet(wallet: string): string {
  return wallet.length > 10 ? `${wallet.slice(0, 4)}…${wallet.slice(-4)}` : wallet;
}

/**
 * Job: push the latest closed SPP cycle weights into the band vault (update_spp_weights).
 * Primary: Sync with owner wallet.
 * Next: /stage-map/settle
 * Hidden: raw scores, instruction bytes, weight history.
 */
export function BandVaultSyncPage() {
  const { bandId } = useParams();
  const id = Number(bandId);
  const wallet = useWallet();
  const band = useBandVault(id);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  async function onSync() {
    setMsg(null);
    try {
      const sig = await band.syncWeights();
      setMsg({ ok: true, text: "Weights synced on DevNet.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not sync" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Band vault</p>
        <h1>{band.vault?.name ?? "Band"} — SPP weights</h1>
        <p className="eh8s-lead">
        Settlements split the band pool by these on-chain weights (sum 100%). Sync after closing
        an SPP cycle.
      </p>
      </header>
      {band.error ? <div className="eh8s-banner bad">{band.error}</div> : null}
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
      {!band.loading && !band.active ? (
        <>
          <p className="eh8s-empty">Activate the band vault first.</p>
          <div className="eh8s-cta-row">
            <Link className="eh8s-btn primary" to={`/bands/${id}/vault/activate`}>
              Activate band vault
            </Link>
          </div>
        </>
      ) : (
        <>
          <ul>
            {band.weights.map((row) => (
              <li key={row.musicianProfileId}>
                {shortWallet(row.wallet)} — {((row.bps ?? 0) / 100).toFixed(2)}%
              </li>
            ))}
          </ul>
          <div className="eh8s-cta-row">
            <button
              type="button"
              className="eh8s-btn primary"
              disabled={band.busy || band.loading || !wallet.connected}
              onClick={() => void onSync()}
            >
              {band.busy ? "Waiting for wallet…" : "Sync with owner wallet"}
            </button>
          </div>
        </>
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/stage-map/settle">
          Settle a concert
        </Link>
        <Link className="eh8s-btn" to={`/bands/${id}`}>
          Back to band
        </Link>
      </div>
    </section>
  );
}
