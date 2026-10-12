import { FormEvent, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useStageMap } from "../hooks/useStageMap";
import { useBands } from "../hooks/useBands";
import { statusText } from "../hooks/statusText";

/**
 * Job: book one show.
 * Primary: Book.
 * Next: stage list.
 * Hidden: settle, claims.
 */
export function StageBookPage() {
  const stage = useStageMap();
  const bands = useBands();
  const [params] = useSearchParams();
  const [venueId, setVenueId] = useState(params.get("venue") || "");
  const [bandId, setBandId] = useState("");
  const [showDate, setShowDate] = useState("2026-11-15");
  const [banner, setBanner] = useState<{ text: string; ok: boolean } | null>(null);

  async function onBook(e: FormEvent) {
    e.preventDefault();
    try {
      const concert = await stage.bookShow({
        venueId: Number(venueId),
        bandId: Number(bandId),
        showDate,
        pipelineWeek: 2,
      });
      setBanner({
        text: `Show booked. Concert ${concert.id} is on the calendar.`,
        ok: true,
      });
    } catch (err) {
      // Spring often omits reason text; map 409 locally instead of Axios "status code 409".
      setBanner({
        text: statusText(
          err,
          {
            409: "That venue, band, and date are already booked.",
            400: "Check venue, band, and date, then try again.",
          },
          "Could not book the show."
        ),
        ok: false,
      });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Booking</p>
        <h1>Book a show</h1>
        <p className="eh8s-lead">One venue, one band, one date.</p>
      </header>
      {banner ? (
        <div className={`eh8s-banner ${banner.ok ? "ok" : "bad"}`}>{banner.text}</div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onBook}>
        <label>
          Venue
          <select value={venueId} onChange={(ev) => setVenueId(ev.target.value)}>
            {stage.venues.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name ?? `Venue ${v.id}`}
              </option>
            ))}
          </select>
        </label>
        <label>
          Band
          <select required value={bandId} onChange={(ev) => setBandId(ev.target.value)}>
            <option value="">Select…</option>
            {bands.bands.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Date
          <input type="date" value={showDate} onChange={(ev) => setShowDate(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary">
          Book show
        </button>
      </form>
      <Link className="eh8s-btn eh8s-back" to="/stage-map">
        Back to stage
      </Link>
    </section>
  );
}
