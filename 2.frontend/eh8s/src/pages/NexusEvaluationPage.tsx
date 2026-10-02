import { Link, useParams } from "react-router-dom";
import { useNexusEvaluation } from "../hooks/useNexus";
import { lines } from "../services/nexusService";

/**
 * Job: read one Score Enigma.
 * Primary: Apply level on-chain (only when NEXUS recommends a change not yet applied).
 * Next: /agents/levels/:musicianProfileId (epic 40 level signing).
 * Hidden: raw rubric JSON, prompt, other evaluations.
 */
export function NexusEvaluationPage() {
  const { evaluationId = "" } = useParams();
  const { loading, error, detail } = useNexusEvaluation(Number(evaluationId));
  const e = detail?.evaluation;
  const musicianId = e?.musicianProfileId;

  const sections: [string, string | null | undefined][] = [
    ["Strengths", e?.strengths],
    ["Errors found", e?.errorsFound],
    ["Exercises", e?.exercises],
  ];

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">NEXUS</p>
        <h1>Score Enigma {e ? e.score : ""}</h1>
      </header>
      {detail ? (
        <p className="eh8s-lead">
          Musician #{detail.musician.id}
          {detail.musician.instrument ? ` (${detail.musician.instrument})` : ""} · now at level{" "}
          {detail.currentLevel}
          {e?.recommendedLevel != null ? ` · NEXUS recommends level ${e.recommendedLevel}` : ""}.
        </p>
      ) : null}
      {loading ? <p className="eh8s-muted-line">Loading evaluation…</p> : null}
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      {detail && e ? (
        <>
          {e.appliedAt ? (
            <div className="eh8s-banner ok">Level applied on {new Date(e.appliedAt).toLocaleDateString()}.</div>
          ) : detail.levelChange ? (
            <div className="eh8s-cta-row">
              <Link className="eh8s-btn primary" to={`/agents/levels/${musicianId}`}>
                Apply level on-chain
              </Link>
            </div>
          ) : (
            <p className="eh8s-muted-line">No level change recommended.</p>
          )}
          {sections.map(([title, text]) =>
            lines(text ?? null).length ? (
              <div key={title}>
                <h2>{title}</h2>
                <ul>
                  {lines(text ?? null).map((line) => (
                    <li key={line}>{line}</li>
                  ))}
                </ul>
              </div>
            ) : null
          )}
          <p className="eh8s-muted-line">
            {e.source === "nexus_ai" ? `Scored by NEXUS (${e.aiModel ?? "Claude"}).` : "Recorded manually."}{" "}
            {detail.audioNote}
          </p>
        </>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to={musicianId ? `/academy/nexus?musician=${musicianId}` : "/academy/nexus"}>
          Back to scores
        </Link>
      </div>
    </section>
  );
}
