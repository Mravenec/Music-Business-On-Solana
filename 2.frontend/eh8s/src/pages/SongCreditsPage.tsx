import { Link } from "react-router-dom";
import { useSongCredits } from "../hooks/useSongCredits";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: see what each song credited to my wallet (deposits and sync licenses).
 * Primary: Back to claims.
 * Next: /stage-map/claim
 * Hidden: pool PDAs, other members' shares, instruction bytes.
 */
export function SongCreditsPage() {
  const credits = useSongCredits();

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Claims</p>
        <h1>Song credits</h1>
        <p className="eh8s-lead">
        Your split of every confirmed song deposit and paid sync license. These credits are
        already in your on-chain pending balance on DevNet.
      </p>
      </header>
      {credits.error ? <div className="eh8s-banner bad">{credits.error}</div> : null}
      {!credits.walletPubkey ? (
        <p className="eh8s-empty">Connect your musician wallet to see song credits.</p>
      ) : (
        <>
          <p className="eh8s-muted-line">Total credited: {credits.totalUsdc.toFixed(2)} USDC</p>
          <div className="eh8s-band-grid">
            {credits.credits.map((c) => (
              <article key={`${c.source}-${c.sourceId}`} className="eh8s-band-card">
                <h3>{c.trackTitle}</h3>
                <p className="eh8s-muted-line">
                  {c.source === "sync" ? "Sync license" : "Deposit"} · {Number(c.creditUsdc).toFixed(2)} USDC
                  ({(c.shareBps / 100).toFixed(2)}% of {c.grossUsdc})
                </p>
                {c.txSignature ? (
                  <a href={explorerTxUrl(c.txSignature)} target="_blank" rel="noreferrer">
                    View transaction
                  </a>
                ) : null}
              </article>
            ))}
            {!credits.credits.length && !credits.loading ? (
              <p className="eh8s-empty">No song credits for this wallet yet.</p>
            ) : null}
          </div>
        </>
      )}
      <Link className="eh8s-btn primary" to="/stage-map/claim">
        Back to claims
      </Link>
    </section>
  );
}
