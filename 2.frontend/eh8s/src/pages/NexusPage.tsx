import { Link, Navigate, useSearchParams } from "react-router-dom";
import { useNexus } from "../hooks/useNexus";
import { useSession } from "../hooks/useSession";

/**
 * Job: see one musician's Score Enigma history from NEXUS.
 * Primary: Request Score Enigma.
 * Next: /academy/nexus/new, or one evaluation (/academy/nexus/:id).
 * Hidden: rubric, strengths, errors, exercises (on the evaluation page), other musicians.
 */
export function NexusPage() {
  const { session } = useSession();
  const [params] = useSearchParams();
  const fromQuery = Number(params.get("musician"));
  const musicianId = Number.isFinite(fromQuery) && fromQuery > 0 ? fromQuery : session?.musicianProfile?.id ?? null;
  const nexus = useNexus(musicianId);

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">NEXUS</p>
        <h1>Score Enigma</h1>
        <p className="eh8s-lead">
        NEXUS reads your rubric and notes and answers with a 0-100 score and a level recommendation.
      </p>
      </header>
      {nexus.status ? (
        <p className="eh8s-muted-line">
          <span className={`eh8s-badge ${nexus.status.aiConfigured ? "ok" : "bad"}`}>
            {nexus.status.aiConfigured ? "AI online" : "AI offline"}
          </span>{" "}
          {nexus.status.aiConfigured
            ? `Model ${nexus.status.model}.`
            : "The server has no Anthropic API key, so new requests answer 503."}
        </p>
      ) : null}
      {nexus.error ? <div className="eh8s-banner bad">{nexus.error}</div> : null}
      {musicianId ? (
        <div className="eh8s-cta-row">
          <Link className="eh8s-btn primary" to={`/academy/nexus/new?musician=${musicianId}`}>
            Request Score Enigma
          </Link>
        </div>
      ) : (
        <p className="eh8s-empty">Create a musician profile first (Academy).</p>
      )}
      <h2>History</h2>
      {nexus.loading ? <p className="eh8s-muted-line">Loading scores…</p> : null}
      {!nexus.loading && musicianId && nexus.evaluations.length === 0 ? (
        <p className="eh8s-empty">No Score Enigma yet.</p>
      ) : null}
      <ul>
        {nexus.evaluations.map((row) => (
          <li key={row.id}>
            <strong>{row.score}</strong> · week of {row.weekStart}
            {row.recommendedLevel != null ? ` · recommends level ${row.recommendedLevel}` : ""}
            {row.appliedAt ? " · applied" : ""}{" "}
            <Link to={`/academy/nexus/${row.id}`}>Open</Link>
          </li>
        ))}
      </ul>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/academy">
          Back to academy
        </Link>
      </div>
    </section>
  );
}
