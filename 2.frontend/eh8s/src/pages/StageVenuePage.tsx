import { Link, useParams } from "react-router-dom";
import { useStageMap } from "../hooks/useStageMap";

/**
 * Job: see one venue.
 * Primary: Book a show.
 * Next: /stage-map/book, /stage-map/venues/:venueId/request, /dates, /onchain
 * Hidden: settle, claims, escrow steps.
 */
export function StageVenuePage() {
  const { venueId } = useParams();
  const stage = useStageMap();
  const venue = stage.venues.find((v) => String(v.id) === venueId);

  if (!stage.loading && !venue) {
    return (
      <section className="eh8s-page">
        <p className="eh8s-empty">That venue was not found.</p>
        <Link className="eh8s-btn eh8s-back" to="/stage-map">
          Back to stage
        </Link>
      </section>
    );
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Venue</p>
        <h1>{venue?.name ?? "Venue"}</h1>
        <p className="eh8s-lead">Book a show here. Settlement stays on its own screen.</p>
      </header>
      <Link className="eh8s-btn primary" to={`/stage-map/book?venue=${venueId}`}>
        Book a show
      </Link>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/stage-map/venues/${venueId}/request`}>
          Request slot
        </Link>
        <Link className="eh8s-btn" to={`/stage-map/venues/${venueId}/dates`}>
          Open dates
        </Link>
        <Link className="eh8s-btn" to={`/stage-map/venues/${venueId}/onchain`}>
          On-chain listing and escrow
        </Link>
        <Link className="eh8s-btn eh8s-back" to="/stage-map">
          Back to stage
        </Link>
      </div>
    </section>
  );
}
