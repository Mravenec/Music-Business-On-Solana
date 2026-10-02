import { FormEvent, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { monthKey } from "../hooks/useStagePins";
import { useVenueSlot } from "../hooks/useVenueSlot";

/**
 * Job: request one open date at one venue for your band.
 * Primary: Request slot.
 * Next: the venue confirms the booking (Stage queue); back to the map.
 * Hidden: escrow, contract text, settlement.
 */
export function StageSlotRequestPage() {
  const { venueId } = useParams();
  const [params] = useSearchParams();
  const month = params.get("month") || monthKey(0);
  const id = Number(venueId);
  const { session } = useSession();
  const me = session?.musicianProfile?.id ?? null;
  const slot = useVenueSlot(id, month, me);
  const [day, setDay] = useState("");
  const [band, setBand] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const days = slot.pin?.availableDays ?? [];
  const bandId = band || (slot.myBands.length === 1 ? String(slot.myBands[0].id) : "");

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const row = await slot.request(day, bandId ? Number(bandId) : undefined);
      const name = slot.myBands.find((b) => b.id === row.bandId)?.name ?? `band #${row.bandId}`;
      setMsg({ ok: true, text: `Requested ${row.showDate} for ${name}. The venue confirms next.` });
      setDay("");
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not request the slot." });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Request slot · {month}</p>
        <h1>{slot.pin?.venue.name ?? "Venue"}</h1>
        <p className="eh8s-lead">{slot.pin?.pinLabel ?? "Pick an open date for your band."}</p>
      </header>
      {me == null ? (
        <div className="eh8s-banner bad">Connect a musician wallet to request a slot.</div>
      ) : null}
      {slot.error ? <div className="eh8s-banner bad">{slot.error}</div> : null}
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      {slot.loading ? <p className="eh8s-muted-line">Loading open dates…</p> : null}
      {!slot.loading && days.length === 0 ? (
        <p className="eh8s-empty">No open dates in {month}.</p>
      ) : null}
      {me != null && days.length > 0 ? (
        <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
          <div className="eh8s-cta-row" role="radiogroup" aria-label="Open dates">
            {days.map((d) => (
              <button
                key={d}
                type="button"
                role="radio"
                aria-checked={day === d}
                className={`eh8s-btn${day === d ? " primary" : ""}`}
                onClick={() => setDay(d)}
              >
                {d}
              </button>
            ))}
          </div>
          {slot.myBands.length > 1 ? (
            <label>
              Band
              <select value={band} onChange={(ev) => setBand(ev.target.value)} required>
                <option value="">Choose one of your bands</option>
                {slot.myBands.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
          {slot.myBands.length === 0 && !slot.loading ? (
            <p className="eh8s-muted-line">Join a band first to request a slot.</p>
          ) : null}
          <button
            type="submit"
            className="eh8s-btn primary"
            disabled={busy || !day || !bandId}
          >
            {busy ? "Requesting…" : "Request slot"}
          </button>
        </form>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/stage-map/venues/${id}/request?month=${monthKey(1)}`}>
          Next month
        </Link>
        <Link className="eh8s-btn" to="/stage-map">
          Back to map
        </Link>
      </div>
    </section>
  );
}
