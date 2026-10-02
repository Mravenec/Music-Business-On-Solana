import { FormEvent, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useTourRoutes } from "../hooks/useTourRoutes";

function isoDay(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * Job: ask ATLAS for one tour route in a date window.
 * Primary: Generate route.
 * Next: the stored plan (/bands/:id/tours/:planId).
 * Hidden: other plans, venue list, income formula.
 */
export function BandTourNewPage() {
  const { bandId = "" } = useParams();
  const id = Number(bandId);
  const navigate = useNavigate();
  const tours = useTourRoutes(id);
  const [windowStart, setWindowStart] = useState(isoDay(1));
  const [windowEnd, setWindowEnd] = useState(isoDay(31));
  const [maxStops, setMaxStops] = useState("6");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      const out = await tours.generate({ windowStart, windowEnd, maxStops: Number(maxStops) });
      navigate(`/bands/${id}/tours/${out.plan.id}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not generate the route.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">ATLAS</p>
        <h1>Plan a route</h1>
        <p className="eh8s-lead">
        ATLAS starts at the best-paying venue, then hops to the nearest one on the next open date.
      </p>
      </header>
      {tours.data && tours.data.activeZones.length === 0 ? (
        <div className="eh8s-banner bad">
          This band has no active zone yet. <Link to="/catalog/reach">Pay zone reach</Link> first.
        </div>
      ) : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          From
          <input type="date" required min={isoDay(0)} value={windowStart} onChange={(ev) => setWindowStart(ev.target.value)} />
        </label>
        <label>
          To
          <input type="date" required min={windowStart} value={windowEnd} onChange={(ev) => setWindowEnd(ev.target.value)} />
        </label>
        <label>
          Max stops (1-10)
          <input type="number" min={1} max={10} required value={maxStops} onChange={(ev) => setMaxStops(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={busy}>
          {busy ? "Routing…" : "Generate route"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/bands/${id}/tours`}>
          Back to routes
        </Link>
      </div>
    </section>
  );
}
