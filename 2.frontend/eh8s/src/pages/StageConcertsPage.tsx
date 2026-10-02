import { Link } from "react-router-dom";
import { useStageMap } from "../hooks/useStageMap";

/**
 * Job: pick the concert you are playing.
 * Primary: Check in my set (per concert).
 * Next: /stage-map/concerts/:concertId/set
 * Hidden: settlement, claims, bookings.
 */
export function StageConcertsPage() {
  const stage = useStageMap();
  const concerts = [...stage.concerts].sort((a, b) => b.id - a.id);

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Concerts</p>
        <h1>Concert check-in</h1>
        <p className="eh8s-lead">Minutes you play count toward your band's concert points.</p>
      </header>
      {stage.error ? <div className="eh8s-banner bad">Could not load concerts.</div> : null}
      {stage.loading ? <p className="eh8s-muted-line">Loading concerts…</p> : null}
      <div className="eh8s-band-grid">
        {concerts.map((c) => (
          <article key={c.id} className="eh8s-band-card">
            <h3>Concert #{c.id}</h3>
            <p className="eh8s-muted-line">
              Band #{c.bandId} · venue #{c.venueId} · {c.status}
            </p>
            <Link className="eh8s-btn primary" to={`/stage-map/concerts/${c.id}/set`}>
              Check in my set
            </Link>
          </article>
        ))}
        {!concerts.length && !stage.loading ? (
          <p className="eh8s-empty">No concerts scheduled yet.</p>
        ) : null}
      </div>
      <Link className="eh8s-btn eh8s-back" to="/stage-map">
        Back to stage
      </Link>
    </section>
  );
}
