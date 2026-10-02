import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useSyncDeals } from "../hooks/useSyncDeals";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: pay one sync license on DevNet (pay_sync_license).
 * Primary: Pay with wallet (licensee).
 * Next: /catalog/tracks/:id/sync
 * Hidden: pool and license PDAs, member profile accounts, instruction bytes.
 */
export function CatalogSyncDealPage() {
  const { trackId, dealId } = useParams();
  const id = Number(trackId);
  const wallet = useWallet();
  const sync = useSyncDeals(id);
  const deal = sync.deals.find((d) => d.id === Number(dealId));
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  async function onPay() {
    if (!deal) return;
    setMsg(null);
    try {
      const sig = await sync.pay(deal.id);
      setMsg({ ok: true, text: "License paid. Artists were credited 80% by the song splits.", sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Payment failed" });
    }
  }

  if (!sync.loading && !deal) {
    return (
      <section className="eh8s-page">
        <p className="eh8s-empty">That sync deal was not found.</p>
        <Link className="eh8s-btn eh8s-back" to={`/catalog/tracks/${id}/sync`}>
          Back to sync deals
        </Link>
      </section>
    );
  }

  const paid = deal?.status === "paid";
  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Sync license</p>
        <h1>{deal?.licenseeName ?? "Sync deal"}</h1>
        <p className="eh8s-lead">{deal?.useDescription ?? "Sync license for this song."}</p>
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
      <p className="eh8s-muted-line">
        {deal?.amountUsdc} USDC · artists {deal?.artistUsdc} · EH8S {deal?.eh8sUsdc}
      </p>
      {paid ? (
        <p className="eh8s-empty">
          Paid.{" "}
          {deal?.payTxSignature ? (
            <a href={explorerTxUrl(deal.payTxSignature)} target="_blank" rel="noreferrer">
              View transaction
            </a>
          ) : null}
        </p>
      ) : (
        <div className="eh8s-cta-row">
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={sync.busy || sync.loading || !wallet.connected}
            onClick={() => void onPay()}
          >
            {sync.busy ? "Waiting for wallet." : "Pay with wallet"}
          </button>
        </div>
      )}
      <Link className="eh8s-btn eh8s-back" to={`/catalog/tracks/${id}/sync`}>
        Back to sync deals
      </Link>
    </section>
  );
}
