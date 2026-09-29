import { Link } from "react-router-dom";
import { useCatalog } from "../hooks/useCatalog";

/**
 * Job: browse tracks.
 * Primary: open a track.
 * Next: /catalog/tracks/:id
 * Hidden: deposit, geo, channel, tours, on-chain ids.
 */
export function CatalogPage() {
  const catalog = useCatalog();

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Music</p>
        <h1>Catalog</h1>
        <p className="eh8s-lead">
          Open one track. Create one if the list is empty — no SQL demo tracks.
        </p>
      </header>
      {catalog.loading ? <p className="eh8s-muted-line">Loading tracks…</p> : null}
      {catalog.error ? (
        <div className="eh8s-banner bad">Could not load the catalog.</div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/catalog/new">
          Add a track
        </Link>
        <Link className="eh8s-btn" to="/catalog/reach">
          Region access
        </Link>
      </div>
      <div className="eh8s-band-grid">
        {catalog.tracks.map((row) => (
          <article key={row.id} className="eh8s-band-card">
            <h3>{row.title}</h3>
            <p className="eh8s-muted-line">{row.provider}</p>
            <Link className="eh8s-btn primary" to={`/catalog/tracks/${row.id}`}>
              Open track
            </Link>
          </article>
        ))}
        {!catalog.tracks.length && !catalog.loading ? (
          <p className="eh8s-empty">No tracks listed yet.</p>
        ) : null}
      </div>
    </section>
  );
}
