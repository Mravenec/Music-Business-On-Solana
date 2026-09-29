import { Link, useParams } from "react-router-dom";
import { useSyncDeals } from "../hooks/useSyncDeals";

/**
 * Job: see this song's sync license deals.
 * Primary: New sync deal.
 * Next: /catalog/tracks/:id/sync/new · /catalog/tracks/:id/sync/:dealId
 * Hidden: 80/20 amounts, wallets, signatures (on the deal page).
 */
export function CatalogSyncPage() {
  const { trackId } = useParams();
  const id = Number(trackId);
  const sync = useSyncDeals(id);

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Sync licensing</p>
      <h1>Sync deals</h1>
      <p className="eh8s-lead">Film, series and ads that license this song. 80% to the artists, 20% to EH8S.</p>
      {sync.error ? <div className="eh8s-banner bad">{sync.error}</div> : null}
      <Link className="eh8s-btn primary" to={`/catalog/tracks/${id}/sync/new`}>
        New sync deal
      </Link>
      <div className="eh8s-band-grid">
        {sync.deals.map((d) => (
          <article key={d.id} className="eh8s-band-card">
            <h3>{d.licenseeName}</h3>
            <p className="eh8s-muted-line">
              {d.amountUsdc} USDC · {d.status === "paid" ? "Paid" : "Waiting for payment"}
            </p>
            <Link className="eh8s-btn" to={`/catalog/tracks/${id}/sync/${d.id}`}>
              Open deal
            </Link>
          </article>
        ))}
        {!sync.deals.length && !sync.loading ? (
          <p className="eh8s-empty">No sync deals for this song yet.</p>
        ) : null}
      </div>
      <Link className="eh8s-btn" to={`/catalog/tracks/${id}`}>
        Back to track
      </Link>
    </section>
  );
}
