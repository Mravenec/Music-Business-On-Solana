import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useBands } from "../hooks/useBands";

/**
 * Job: schedule one rehearsal.
 * Primary: Create session.
 * Next: band detail.
 * Hidden: check-in, cycles, create band.
 */
export function BandRehearsalNewPage() {
  const { bandId } = useParams();
  const bandsApi = useBands();
  const id = Number(bandId);
  const [notes, setNotes] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (Number.isFinite(id)) bandsApi.setSelectedBandId(id);
  }, [id, bandsApi.setSelectedBandId]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      await bandsApi.createSession({ bandId: id, notes: notes || undefined });
      setMsg("Rehearsal scheduled.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not schedule");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Rehearsal</p>
        <h1>Schedule a rehearsal</h1>
        <p className="eh8s-lead">One session for this band.</p>
      </header>
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("could") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Notes
          <textarea value={notes} onChange={(ev) => setNotes(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary">
          Create session
        </button>
      </form>
      <Link className="eh8s-btn eh8s-back" to={`/bands/${id}`}>
        Back to band
      </Link>
    </section>
  );
}
