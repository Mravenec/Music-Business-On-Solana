import { FormEvent, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAcademy } from "../hooks/useAcademy";
import { useSession } from "../hooks/useSession";

/**
 * Job: record one Enigma score.
 * Primary: Save evaluation.
 * Next: academy list.
 * Hidden: plans grid, payment, wallet.
 */
export function AcademyEvaluatePage() {
  const academy = useAcademy();
  const { session, refresh: refreshSession } = useSession();
  const [score, setScore] = useState("80");
  const [notes, setNotes] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const musicianId = session?.musicianProfile?.id ?? academy.musicians[0]?.id;

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  async function onEvaluate(e: FormEvent) {
    e.preventDefault();
    if (!musicianId) {
      setMsg("No musician profile to score yet.");
      return;
    }
    try {
      await academy.evaluate({
        musicianProfileId: musicianId,
        score: Number(score),
        notes: notes || undefined,
      });
      await refreshSession();
      setMsg("Score saved.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not save score");
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Score</p>
      <h1>Record a score</h1>
      <p className="eh8s-lead">One evaluation at a time, from 0 to 100.</p>
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("could") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onEvaluate}>
        <label>
          Score
          <input
            type="number"
            min={0}
            max={100}
            required
            value={score}
            onChange={(ev) => setScore(ev.target.value)}
          />
        </label>
        <label>
          Notes
          <textarea value={notes} onChange={(ev) => setNotes(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary">
          Save evaluation
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/academy">
          Back to plans
        </Link>
      </div>
    </section>
  );
}
