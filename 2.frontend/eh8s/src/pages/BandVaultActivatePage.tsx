import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useBandVault } from "../hooks/useBandVault";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: open this band's on-chain vault (create_band) so concerts split by SPP weight.
 * Primary: Activate with owner wallet.
 * Next: /bands/:id/vault/sync
 * Hidden: PDA seeds, instruction bytes, other bands.
 */
export function BandVaultActivatePage() {
  const { bandId } = useParams();
  const id = Number(bandId);
  const wallet = useWallet();
  const band = useBandVault(id);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  async function onActivate() {
    setMsg(null);
    try {
      const sig = await band.activate();
      setMsg({ ok: true, text: "Band vault is live on DevNet with equal weights.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not activate" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Band vault</p>
        <h1>{band.vault?.name ?? "Band"} — activate on-chain</h1>
        <p className="eh8s-lead">
        The owner opens one vault per band. Every member needs a linked wallet; concerts then
        credit each member's on-chain pending by weight.
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
      {band.active ? (
        <p className="eh8s-empty">This band vault is already active.</p>
      ) : (
        <div className="eh8s-cta-row">
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={band.busy || band.loading || !wallet.connected}
            onClick={() => void onActivate()}
          >
            {band.busy ? "Waiting for wallet…" : "Activate with owner wallet"}
          </button>
        </div>
      )}
      <div className="eh8s-cta-row">
        {band.active ? (
          <Link className="eh8s-btn primary" to={`/bands/${id}/vault/sync`}>
            Sync SPP weights
          </Link>
        ) : null}
        <Link className="eh8s-btn" to={`/bands/${id}`}>
          Back to band
        </Link>
      </div>
    </section>
  );
}
