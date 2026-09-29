import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useSetCheckin } from "../hooks/useSetCheckin";

/**
 * Job: check in the start and end of your set at one concert.
 * Primary: Start my set, then End my set.
 * Next: minutes played shown here; back to concerts.
 * Hidden: other members' sets, settlement, SPP totals.
 */
export function StageConcertSetPage() {
  const { concertId } = useParams();
  const id = Number(concertId);
  const { session } = useSession();
  const me = session?.musicianProfile?.id ?? null;
  const set = useSetCheckin(id, me);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function run(step: () => Promise<void>, done: string) {
    setBusy(true);
    try {
      await step();
      setMsg({ ok: true, text: done });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not save." });
    } finally {
      setBusy(false);
    }
  }

  const mine = set.mine;
  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Concert #{id}</p>
      <h1>My set</h1>
      <p className="eh8s-lead">Tap when you go on stage and when you finish.</p>
      {me == null ? (
        <div className="eh8s-banner bad">Connect the wallet of a band member to check in.</div>
      ) : null}
      {set.error ? <div className="eh8s-banner bad">{set.error}</div> : null}
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      {set.loading ? <p className="eh8s-muted-line">Loading…</p> : null}
      {!set.loading && me != null && !mine ? (
        <button
          type="button"
          className="eh8s-btn primary"
          disabled={busy}
          onClick={() => run(set.start, "Set started. Tap End my set when you finish.")}
        >
          {busy ? "Saving…" : "Start my set"}
        </button>
      ) : null}
      {mine && !mine.setEndedAt ? (
        <>
          <p className="eh8s-muted-line">Started at {mine.setStartedAt.replace("T", " ")}</p>
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={busy}
            onClick={() => run(set.end, "Set ended.")}
          >
            {busy ? "Saving…" : "End my set"}
          </button>
        </>
      ) : null}
      {mine?.setEndedAt ? (
        <div className="eh8s-panel">
          <h2>{mine.minutesPlayed ?? 0} minutes played</h2>
          <p className="eh8s-muted-line">
            Counted against the show length when the SPP cycle closes.
          </p>
        </div>
      ) : null}
      <Link className="eh8s-btn" to="/stage-map/concerts">
        Back to concerts
      </Link>
    </section>
  );
}
