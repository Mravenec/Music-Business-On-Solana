import { Link, useParams } from "react-router-dom";
import { useTourPlan } from "../hooks/useTourRoutes";

/**
 * Job: read one ATLAS route, stop by stop.
 * Primary: Open venue (per stop) to request that slot.
 * Next: /stage-map/venues/:venueId
 * Hidden: coordinates, income formula, other plans.
 */
export function BandTourPage() {
  const { bandId = "", planId = "" } = useParams();
  const { loading, error, detail } = useTourPlan(Number(planId));
  const plan = detail?.plan;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">ATLAS</p>
        <h1>{plan?.title ?? "Tour plan"}</h1>
      </header>
      {plan?.routeNote ? <p className="eh8s-lead">{plan.routeNote}</p> : null}
      {loading ? <p className="eh8s-muted-line">Loading plan…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {detail && plan ? (
        <>
          <div className="eh8s-stats">
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Stops</span>
              <strong>{detail.stops.length}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Distance</span>
              <strong>{plan.totalKm ?? 0} km</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Projected income</span>
              <strong>{plan.projectedIncomeUsdc ?? 0} USDC</strong>
            </article>
          </div>
          <div className="eh8s-table-wrap">
            <table className="eh8s-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Date</th>
                  <th>Venue</th>
                  <th>Leg</th>
                  <th>Income</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {detail.stops.map(({ stop, venue }) => (
                  <tr key={stop.id}>
                    <td>{stop.stopOrder}</td>
                    <td>{stop.showDate ?? "no open date"}</td>
                    <td>
                      {venue?.name ?? `Venue #${stop.venueId}`}
                      {venue?.city ? ` · ${venue.city}` : ""}
                    </td>
                    <td>{stop.legKm ?? 0} km</td>
                    <td>{stop.projectedIncomeUsdc ?? 0} USDC</td>
                    <td>
                      <Link to={`/stage-map/venues/${stop.venueId}`}>Open venue</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/bands/${bandId}/tours`}>
          Back to routes
        </Link>
      </div>
    </section>
  );
}
