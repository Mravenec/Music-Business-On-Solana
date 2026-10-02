import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useBands } from "../hooks/useBands";
import { useSession } from "../hooks/useSession";

/**
 * Job: check in to one rehearsal.
 * Primary: Check in.
 * Next: /bands/:id/rehearsals/:sessionId/rate, then band detail.
 * Hidden: create band, open/close cycle.
 */
export function BandCheckInPage() {
  const { bandId, sessionId } = useParams();
  const bandsApi = useBands();
  const { session } = useSession();
  const id = Number(bandId);
  const sid = Number(sessionId);
  const [musicianId, setMusicianId] = useState(
    session?.musicianProfile?.id ? String(session.musicianProfile.id) : ""
  );
  const [lateMinutes, setLateMinutes] = useState("0");
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (Number.isFinite(id)) bandsApi.setSelectedBandId(id);
  }, [id, bandsApi.setSelectedBandId]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      await bandsApi.checkIn({
        rehearsalSessionId: sid,
        musicianProfileId: Number(musicianId),
        lateMinutes: Number(lateMinutes),
      });
      setMsg("Checked in.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not check in");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Attendance</p>
        <h1>Check in</h1>
        <p className="eh8s-lead">One rehearsal, one musician.</p>
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
          Musician profile id
          <input
            required
            value={musicianId}
            onChange={(ev) => setMusicianId(ev.target.value)}
          />
        </label>
        <label>
          Late minutes
          <input
            type="number"
            min={0}
            value={lateMinutes}
            onChange={(ev) => setLateMinutes(ev.target.value)}
          />
        </label>
        <button type="submit" className="eh8s-btn primary">
          Check in
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/bands/${id}/rehearsals/${sid}/rate`}>
          Rate bandmates
        </Link>
        <Link className="eh8s-btn" to={`/bands/${id}`}>
          Back to band
        </Link>
      </div>
    </section>
  );
}
