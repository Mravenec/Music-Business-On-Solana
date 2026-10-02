import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useOps } from "../hooks/useOps";
import type { OwnerDecision } from "../services/opsService";

function answeredLine(row: OwnerDecision): string {
  const verb = row.status === "approved" ? "Approved" : "Rejected";
  if (row.answeredVia === "slack") {
    return `${verb} in Slack${row.answeredBy ? ` by ${row.answeredBy}` : ""}`;
  }
  if (row.answeredVia === "web") return `${verb} in the web inbox`;
  return verb;
}

/**
 * Job: see who answered each agent decision, and where (web inbox or Slack).
 * Primary: none (read-only list); back to pending decisions.
 * Next: /owner/decisions
 * Hidden: decision payload JSON, agent task status.
 */
export function OwnerDecisionsAnsweredPage() {
  const ops = useOps();
  const answered = useMemo(
    () =>
      ops.decisions
        .filter((d) => d.status !== "pending")
        .sort((a, b) => String(b.resolvedAt ?? "").localeCompare(String(a.resolvedAt ?? ""))),
    [ops.decisions]
  );

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Decisions</p>
        <h1>Answered decisions</h1>
        <p className="eh8s-lead">Each decision is answered once - the first answer wins.</p>
      </header>
      {ops.error ? <div className="eh8s-banner bad">{ops.error}</div> : null}
      {ops.loading ? <p className="eh8s-muted-line">Loading decisions.</p> : null}
      {ops.error ? null : !ops.loading && answered.length === 0 ? (
        <p className="eh8s-empty">No answered decisions yet.</p>
      ) : (
        answered.map((row) => (
          <article key={row.id} className="eh8s-score-card">
            <strong>{row.title ?? `Decision ${row.id}`}</strong>
            <p className="eh8s-muted-line">{answeredLine(row)}</p>
            {row.rejectReason ? <p className="eh8s-muted-line">Reason: {row.rejectReason}</p> : null}
            {row.resolvedAt ? (
              <p className="eh8s-muted-line">{new Date(row.resolvedAt).toLocaleString()}</p>
            ) : null}
          </article>
        ))
      )}
      <Link className="eh8s-btn primary eh8s-back" to="/owner/decisions">
        Back to pending decisions
      </Link>
    </section>
  );
}
