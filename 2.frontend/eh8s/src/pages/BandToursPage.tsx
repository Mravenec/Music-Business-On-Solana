import { Link, useParams } from "react-router-dom";
import { useTourRoutes } from "../hooks/useTourRoutes";

/**
 * Job: see one band's ATLAS tour plans and the zones it can tour.
 * Primary: Plan a route.
 * Next: /bands/:id/tours/new, or one plan (/bands/:id/tours/:planId).
 * Hidden: stops, distances, venue coordinates (on the plan page), zone payment.
 */
export function BandToursPage() {
  const { bandId = "" } = useParams();
  const id = Number(bandId);
  const tours = useTourRoutes(id);
  const zones = tours.data?.activeZones ?? [];
  const plans = tours.data?.plans ?? [];

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">ATLAS</p>
        <h1>Tour routes{tours.data ? ` for ${tours.data.band.name}` : ""}</h1>
        <p className="eh8s-lead">Routes cover approved venues inside the zones this band subscribed to.</p>
      </header>
      {tours.error ? <div className="eh8s-banner bad">{tours.error}</div> : null}
      {tours.data ? (
        <p className="eh8s-muted-line">
          Active zones: {zones.length ? zones.join(", ") : "none yet"}
          {zones.length ? null : (
            <>
              {" · "}
              <Link to="/catalog/reach">Pay zone reach</Link>
            </>
          )}
        </p>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to={`/bands/${id}/tours/new`}>
          Plan a route
        </Link>
      </div>
      <h2>Plans</h2>
      {tours.loading ? <p className="eh8s-muted-line">Loading plans…</p> : null}
      {!tours.loading && !tours.error && plans.length === 0 ? (
        <p className="eh8s-empty">No tour plans yet.</p>
      ) : null}
      <ul>
        {plans.map(({ plan: p, stops }) => (
          <li key={p.id}>
            {p.title} · {stops.length} stops
            {p.totalKm != null ? ` · ${p.totalKm} km` : ""}
            {p.projectedIncomeUsdc != null ? ` · ${p.projectedIncomeUsdc} USDC` : ""}{" "}
            <Link to={`/bands/${id}/tours/${p.id}`}>Open</Link>
          </li>
        ))}
      </ul>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to={`/bands/${id}`}>
          Back to band
        </Link>
      </div>
    </section>
  );
}
