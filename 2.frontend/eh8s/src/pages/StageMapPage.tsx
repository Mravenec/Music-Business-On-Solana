import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { CircleMarker, MapContainer, Popup, TileLayer } from "react-leaflet";
import L from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import "leaflet/dist/leaflet.css";
import { monthKey, useStagePins } from "../hooks/useStagePins";
import type { PinColor } from "../services/stageMapService";

const DefaultIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
L.Marker.prototype.options.icon = DefaultIcon;

const PIN_HEX: Record<PinColor, string> = {
  purple: "#8b5cf6",
  yellow: "#eab308",
  green: "#22c55e",
  red: "#ef4444",
};

const LEGEND: { color: PinColor; text: string }[] = [
  { color: "green", text: "Open dates" },
  { color: "yellow", text: "Confirmed show" },
  { color: "red", text: "No availability" },
  { color: "purple", text: "EH8S partner" },
];

/**
 * Job: find a venue on the map.
 * Primary: Request slot (from a pin popup with open dates).
 * Next: /stage-map/venues/:id/request, /stage-map/venues/:id
 * Hidden: settle, claim, PDA dumps, booking escrow.
 */
export function StageMapPage() {
  const [offset, setOffset] = useState(0);
  const month = monthKey(offset);
  const stage = useStagePins(month);
  const center = useMemo<[number, number]>(() => {
    const first = stage.pins[0]?.venue;
    if (first?.latitude != null && first?.longitude != null) {
      return [Number(first.latitude), Number(first.longitude)];
    }
    return [19.4326, -99.1332];
  }, [stage.pins]);

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Venues · {month}</p>
        <h1>Stage</h1>
        <p className="eh8s-lead">Pin colors come from open dates and confirmed bookings.</p>
      </header>
      {stage.loading ? <p className="eh8s-muted-line">Loading venues…</p> : null}
      {stage.error ? <div className="eh8s-banner bad">{stage.error}</div> : null}
      <div className="eh8s-cta-row" role="radiogroup" aria-label="Month">
        <button
          type="button"
          role="radio"
          aria-checked={offset === 0}
          className={`eh8s-btn${offset === 0 ? " primary" : ""}`}
          onClick={() => setOffset(0)}
        >
          This month
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={offset === 1}
          className={`eh8s-btn${offset === 1 ? " primary" : ""}`}
          onClick={() => setOffset(1)}
        >
          Next month
        </button>
      </div>
      <p className="eh8s-muted-line" aria-label="Pin legend">
        {LEGEND.map((l) => (
          <span key={l.color} style={{ marginRight: 14, whiteSpace: "nowrap" }}>
            <span
              aria-hidden
              style={{
                display: "inline-block",
                width: 10,
                height: 10,
                borderRadius: 5,
                background: PIN_HEX[l.color],
                marginRight: 6,
              }}
            />
            {l.text}
          </span>
        ))}
      </p>
      <div className="eh8s-map-wrap" style={{ minHeight: 280 }}>
        <MapContainer center={center} zoom={5} style={{ height: 280, width: "100%" }}>
          <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
          {stage.pins.map((p) =>
            p.venue.latitude != null && p.venue.longitude != null ? (
              <CircleMarker
                key={p.venueId}
                center={[Number(p.venue.latitude), Number(p.venue.longitude)]}
                radius={10}
                pathOptions={{ color: PIN_HEX[p.pin], fillColor: PIN_HEX[p.pin], fillOpacity: 0.85 }}
              >
                <Popup>
                  <strong>{p.venue.name}</strong>
                  <br />
                  {p.pinLabel}
                  <br />
                  Capacity {p.venue.capacity} · {p.contractType ?? "Contract"} · rating{" "}
                  {p.venue.eh8sRating}/5
                  <br />
                  {p.venue.preferredGenres ?? "Any genre"}
                  <br />
                  {p.availableDays.length
                    ? `Open: ${p.availableDays.slice(0, 6).join(", ")}`
                    : "No open dates this month"}
                  <br />
                  {p.availableDays.length ? (
                    <Link to={`/stage-map/venues/${p.venueId}/request?month=${month}`}>
                      Request slot
                    </Link>
                  ) : (
                    <Link to={`/stage-map/venues/${p.venueId}`}>Open venue</Link>
                  )}
                </Popup>
              </CircleMarker>
            ) : null
          )}
        </MapContainer>
      </div>
      <div className="eh8s-band-grid">
        {stage.pins.map((p) => (
          <article key={p.venueId} className="eh8s-band-card">
            <h3>
              <span
                aria-label={p.pin}
                style={{
                  display: "inline-block",
                  width: 10,
                  height: 10,
                  borderRadius: 5,
                  background: PIN_HEX[p.pin],
                  marginRight: 8,
                }}
              />
              {p.venue.name}
            </h3>
            <p className="eh8s-muted-line">{p.pinLabel}</p>
            <Link className="eh8s-btn" to={`/stage-map/venues/${p.venueId}`}>
              Open venue
            </Link>
          </article>
        ))}
        {!stage.pins.length && !stage.loading ? (
          <p className="eh8s-empty">No venues listed yet.</p>
        ) : null}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/stage-map/book">
          Book a show
        </Link>
        <Link className="eh8s-btn" to="/stage-map/concerts">
          Concert check-in
        </Link>
        <Link className="eh8s-btn" to="/stage-map/venues/new">
          List a venue
        </Link>
        <Link className="eh8s-btn" to="/stage-map/settle">
          Settle a concert
        </Link>
        <Link className="eh8s-btn" to="/stage-map/claim">
          Claim royalties
        </Link>
      </div>
    </section>
  );
}
