import { Link } from "react-router-dom";
import { useBands } from "../hooks/useBands";

/**
 * Job: pick a band.
 * Primary: open a band or create.
 * Next: /bands/:id or /bands/new
 * Hidden: members forms, rehearsals, cycles, vault ids.
 */
export function BandsPage() {
  const bandsApi = useBands();

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Ensembles</p>
        <h1>Bands</h1>
        <p className="eh8s-lead">Open one band. Adding members happens there.</p>
      </header>
      {bandsApi.loading ? <p className="eh8s-muted-line">Loading bands…</p> : null}
      {bandsApi.error ? (
        <div className="eh8s-banner bad">Could not load bands. Try again shortly.</div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/bands/new">
          Create band
        </Link>
      </div>
      <div className="eh8s-band-grid">
        {bandsApi.bands.map((row) => (
          <article key={row.id} className="eh8s-band-card">
            <h3>{row.name}</h3>
            <p className="eh8s-band-code">{row.code}</p>
            <Link className="eh8s-btn primary" to={`/bands/${row.id}`}>
              Open band
            </Link>
          </article>
        ))}
        {!bandsApi.bands.length && !bandsApi.loading ? (
          <p className="eh8s-empty">No bands yet. Create one to get started.</p>
        ) : null}
      </div>
    </section>
  );
}
