import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useSongPool } from "../hooks/useSongPool";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";
import type { SongPoolSplit } from "../services/songRoyaltyService";

function shortKey(key: string): string {
  return `${key.slice(0, 4)}…${key.slice(-4)}`;
}

/**
 * Job: put this song's royalty splits on-chain (create_royalty_pool).
 * Primary: Activate with wallet (owner or WAVE agent).
 * Next: /catalog/tracks/:id/deposit
 * Hidden: PDA seeds, instruction bytes, other songs.
 */
export function CatalogSongPoolPage() {
  const { trackId } = useParams();
  const id = Number(trackId);
  const wallet = useWallet();
  const song = useSongPool(id);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const active = Boolean(song.pool?.active);
  const rows: SongPoolSplit[] = (active ? song.pool?.splits : song.pool?.proposedSplits) ?? [];

  async function onActivate() {
    setMsg(null);
    try {
      const sig = await song.activate();
      setMsg({ ok: true, text: "Song pool is live on DevNet.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not activate" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Song pool</p>
        <h1>{song.pool?.title ?? "Song"} - splits</h1>
        <p className="eh8s-lead">
        {active
          ? "These splits are fixed on-chain. Every deposit and sync license credits members by them."
          : "One pool per song. The owner or a WAVE agent signs once; members need an on-chain musician profile."}
      </p>
      </header>
      {song.error ? <div className="eh8s-banner bad">{song.error}</div> : null}
      {song.pool?.proposedError ? (
        <div className="eh8s-banner bad">{song.pool.proposedError}</div>
      ) : null}
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
      <ul className="eh8s-panel">
        {rows.map((r) => (
          <li key={r.musicianProfileId}>
            Musician #{r.musicianProfileId} · {shortKey(r.wallet)} · {(r.bps / 100).toFixed(2)}%
          </li>
        ))}
        {!rows.length && !song.loading ? <li className="eh8s-empty">No members to split yet.</li> : null}
      </ul>
      {active && song.pool?.onchainExists ? (
        <p className="eh8s-muted-line">
          Deposited {song.pool.onchainTotalUsdc} USDC · sync {song.pool.onchainSyncTotalUsdc} USDC
        </p>
      ) : null}
      {active ? null : (
        <div className="eh8s-cta-row">
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={song.busy || song.loading || !rows.length || !wallet.connected}
            onClick={() => void onActivate()}
          >
            {song.busy ? "Waiting for wallet." : "Activate with wallet"}
          </button>
        </div>
      )}
      <div className="eh8s-cta-row">
        {active ? (
          <Link className="eh8s-btn primary" to={`/catalog/tracks/${id}/deposit`}>
            Deposit royalties
          </Link>
        ) : null}
        <Link className="eh8s-btn eh8s-back" to={`/catalog/tracks/${id}`}>
          Back to track
        </Link>
      </div>
    </section>
  );
}
