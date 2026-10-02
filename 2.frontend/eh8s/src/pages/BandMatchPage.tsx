import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useBandMatch } from "../hooks/useBandMatch";

/**
 * Job: see which musicians HARMONY suggests for one band.
 * Primary: Run HARMONY.
 * Next: Add member (/bands/:id/members/add) with a suggested musician.
 * Hidden: other bands, per-factor weights (the reason line explains them), rehearsals.
 */
export function BandMatchPage() {
  const { bandId = "" } = useParams();
  const id = Number(bandId);
  const match = useBandMatch(id);
  const [withAi, setWithAi] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const rows = match.data?.suggestions ?? [];

  async function onRun() {
    setMsg(null);
    try {
      const out = await match.run(withAi);
      setMsg(
        out.aiError
          ? { ok: false, text: `Ranking saved; Claude rationale failed: ${out.aiError}` }
          : { ok: true, text: `Ranked ${out.suggestions.length} musicians${out.aiUsed ? " with Claude rationale" : ""}.` }
      );
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not run HARMONY." });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">HARMONY</p>
        <h1>Find musicians{match.data ? ` for ${match.data.band.name}` : ""}</h1>
        <p className="eh8s-lead">
        Ranked by instrument gaps, level closeness, country, and shared genres (0-100).
      </p>
      </header>
      {match.error ? <div className="eh8s-banner bad">{match.error}</div> : null}
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      <div className="eh8s-cta-row">
        <button type="button" className="eh8s-btn primary" disabled={match.busy} onClick={() => void onRun()}>
          {match.busy ? "Ranking…" : "Run HARMONY"}
        </button>
        {match.data?.aiConfigured ? (
          <label className="eh8s-check">
            <input type="checkbox" checked={withAi} onChange={(ev) => setWithAi(ev.target.checked)} /> Add
            Claude rationale
          </label>
        ) : null}
      </div>
      {match.loading ? <p className="eh8s-muted-line">Loading suggestions…</p> : null}
      {!match.loading && !match.error && rows.length === 0 ? (
        <p className="eh8s-empty">No suggestions yet. Run HARMONY to rank candidates.</p>
      ) : null}
      <div className="eh8s-score-list">
        {rows.map((row) => (
          <article key={row.suggestion.id} className="eh8s-score-card">
            <div className="eh8s-score-top">
              <strong>
                Musician #{row.suggestion.musicianProfileId}
                {row.instrument ? ` · ${row.instrument}` : ""}
                {row.level != null ? ` · level ${row.level}` : ""}
              </strong>
              <span className="eh8s-score-pts">{row.suggestion.score}</span>
            </div>
            <p className="eh8s-muted-line">{row.suggestion.reason}</p>
            {row.suggestion.aiRationale ? <p>{row.suggestion.aiRationale}</p> : null}
          </article>
        ))}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/bands/${id}/members/add`}>
          Add member
        </Link>
        <Link className="eh8s-btn eh8s-back" to={`/bands/${id}`}>
          Back to band
        </Link>
      </div>
    </section>
  );
}
