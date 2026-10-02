import { FormEvent, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useCreativeRatings } from "../hooks/useCreativeRatings";
import { useSession } from "../hooks/useSession";

const SCORES = [1, 2, 3, 4, 5];

/**
 * Job: rate one bandmate's creative contribution for one rehearsal.
 * Primary: Save rating.
 * Next: the next bandmate on the same screen, then back to the band.
 * Hidden: other rehearsals, SPP totals, other members' ratings.
 */
export function BandRatePage() {
  const { bandId, sessionId } = useParams();
  const id = Number(bandId);
  const sid = Number(sessionId);
  const { session } = useSession();
  const me = session?.musicianProfile?.id ?? null;
  const ratings = useCreativeRatings(id, sid, me);
  const [ratee, setRatee] = useState("");
  const [score, setScore] = useState(0);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const target = ratee || (ratings.toRate[0] ? String(ratings.toRate[0].musicianProfileId) : "");

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await ratings.rate(Number(target), score);
      setMsg({ ok: true, text: "Rating saved." });
      setRatee("");
      setScore(0);
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not save the rating." });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Creative contribution</p>
        <h1>Rate a bandmate</h1>
        <p className="eh8s-lead">
        One score from 1 to 5 per bandmate. The cycle average becomes their creative points.
      </p>
      </header>
      {me == null ? (
        <div className="eh8s-banner bad">Connect the wallet of a band member to rate.</div>
      ) : null}
      {ratings.error ? <div className="eh8s-banner bad">{ratings.error}</div> : null}
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      {ratings.loading ? <p className="eh8s-muted-line">Loading rehearsal…</p> : null}
      {!ratings.loading && me != null && ratings.toRate.length === 0 ? (
        <p className="eh8s-empty">You rated every bandmate for this rehearsal.</p>
      ) : null}
      {me != null && ratings.toRate.length > 0 ? (
        <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
          <label>
            Bandmate
            <select value={target} onChange={(ev) => setRatee(ev.target.value)}>
              {ratings.toRate.map((m) => (
                <option key={m.id} value={m.musicianProfileId}>
                  Musician #{m.musicianProfileId} · {m.roleInBand}
                </option>
              ))}
            </select>
          </label>
          <div className="eh8s-cta-row" role="radiogroup" aria-label="Score">
            {SCORES.map((s) => (
              <button
                key={s}
                type="button"
                role="radio"
                aria-checked={score === s}
                className={`eh8s-btn${score === s ? " primary" : ""}`}
                onClick={() => setScore(s)}
              >
                {s}
              </button>
            ))}
          </div>
          <button type="submit" className="eh8s-btn primary" disabled={busy || score === 0 || !target}>
            {busy ? "Saving…" : "Save rating"}
          </button>
        </form>
      ) : null}
      {ratings.mine.length ? (
        <p className="eh8s-muted-line">You rated {ratings.mine.length} bandmate(s) here.</p>
      ) : null}
      <Link className="eh8s-btn" to={`/bands/${id}`}>
        Back to band
      </Link>
    </section>
  );
}
