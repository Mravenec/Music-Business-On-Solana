import { FormEvent, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useCatalog } from "../hooks/useCatalog";
import { useSongPool } from "../hooks/useSongPool";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: deposit royalties into this song's pool on DevNet (deposit_royalties).
 * Primary: Pay with wallet (owner or WAVE agent).
 * Next: /catalog/tracks/:id
 * Hidden: pool PDA, member profile accounts, instruction bytes.
 */
export function CatalogDepositPage() {
  const { trackId } = useParams();
  const id = Number(trackId);
  const catalog = useCatalog();
  const song = useSongPool(id);
  const wallet = useWallet();
  const [amount, setAmount] = useState("50");
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const active = Boolean(song.pool?.active);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setMsg({ ok: false, text: "Enter an amount above 0." });
      return;
    }
    setMsg(null);
    try {
      const created = await catalog.depositRoyalty({
        trackId: id,
        royaltyTypeId: 1,
        amountUsdc: value,
      });
      const sig = await song.payDeposit(created.id);
      await catalog.refresh();
      setMsg({ ok: true, text: "Deposit confirmed and credited by the song splits.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Deposit failed" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Royalties</p>
        <h1>Deposit royalties</h1>
        <p className="eh8s-lead">
        DevNet USDC into {song.pool?.title ?? "this song"}'s pool. Members are credited by its
        on-chain splits.
      </p>
      </header>
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
      {!song.loading && !active ? (
        <>
          <p className="eh8s-empty">Activate the song pool before depositing.</p>
          <Link className="eh8s-btn primary" to={`/catalog/tracks/${id}/pool`}>
            Activate song pool
          </Link>
        </>
      ) : (
        <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
          <label>
            Amount (USDC)
            <input value={amount} onChange={(ev) => setAmount(ev.target.value)} />
          </label>
          <button
            type="submit"
            className="eh8s-btn primary"
            disabled={song.busy || song.loading || !wallet.connected}
          >
            {song.busy ? "Waiting for wallet." : "Pay with wallet"}
          </button>
        </form>
      )}
      <Link className="eh8s-btn eh8s-back" to={`/catalog/tracks/${id}`}>
        Back to track
      </Link>
    </section>
  );
}
