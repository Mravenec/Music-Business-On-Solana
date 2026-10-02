import { FormEvent, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { monthKey } from "../hooks/useStagePins";
import { useVenueSlot } from "../hooks/useVenueSlot";

/**
 * Job: open one date on this venue's calendar.
 * Primary: Open this date.
 * Next: the pin turns green; musicians can Request slot.
 * Hidden: bookings, escrow, listing approval.
 */
export function StageVenueDatesPage() {
  const { venueId } = useParams();
  const id = Number(venueId);
  const { session } = useSession();
  const [month, setMonth] = useState(monthKey(0));
  const slot = useVenueSlot(id, month, session?.musicianProfile?.id ?? null);
  const [date, setDate] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await slot.openDate(date);
      setMsg({ ok: true, text: `${date} is open for requests.` });
      setMonth(date.slice(0, 7));
      setDate("");
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not open the date." });
    } finally {
      setBusy(false);
    }
  }

  const days = slot.pin?.availableDays ?? [];
  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Venue calendar</p>
        <h1>{slot.pin?.venue.name ?? "Venue"}</h1>
        <p className="eh8s-lead">Open dates make the map pin green and let bands request a slot.</p>
      </header>
      {slot.error ? <div className="eh8s-banner bad">{slot.error}</div> : null}
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Date
          <input type="date" required value={date} onChange={(ev) => setDate(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={busy || !date}>
          {busy ? "Opening…" : "Open this date"}
        </button>
      </form>
      <p className="eh8s-muted-line">
        Open in {month}: {days.length ? days.join(", ") : "none"}
      </p>
      <Link className="eh8s-btn" to={`/stage-map/venues/${id}`}>
        Back to venue
      </Link>
    </section>
  );
}
