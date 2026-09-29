import { FormEvent, useState } from "react";
import { useAgentEvents } from "../hooks/useAgentEvents";

function semTone(color: string | undefined): string {
  const s = (color ?? "").toLowerCase();
  if (s === "green") return "ok";
  if (s === "yellow") return "info";
  if (s === "red") return "bad";
  return "muted";
}

/**
 * Agent events panel — live tick + event log (no mocks).
 */
export function AgentEventsPage() {
  const panel = useAgentEvents();
  const [msg, setMsg] = useState<string | null>(null);
  const [agentCode, setAgentCode] = useState("STAGE");
  const [semaphore, setSemaphore] = useState<"green" | "yellow" | "red">("yellow");
  const [summary, setSummary] = useState("Harness agent tick");

  async function onTick(e: FormEvent) {
    e.preventDefault();
    try {
      const row = await panel.tickAgent({
        agentCode,
        semaphore,
        eventType: "ui_tick",
        summary,
        detail: `UI tick for ${agentCode} (${semaphore})`,
      });
      await panel.refresh();
      setMsg(
        row.ownerDecisionId
          ? `Event #${row.id} stored; owner decision #${row.ownerDecisionId} pending.`
          : `Event #${row.id} stored (green — no decision).`,
      );
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Tick failed");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Ops automation</p>
        <h1>Agent events</h1>
        <p className="eh8s-lead">
          Run deterministic agent ticks that append the event log and create
          owner decisions for yellow/red semaphores.
        </p>
      </header>

      {panel.loading ? <p className="eh8s-muted-line">Loading events…</p> : null}
      {panel.error ? (
        <div className="eh8s-banner bad">
          Agent events unavailable: {panel.error}
        </div>
      ) : null}
      {msg ? <div className="eh8s-banner ok">{msg}</div> : null}

      <div className="eh8s-stats">
        <article className="eh8s-stat">
          <span className="eh8s-stat-label">Events</span>
          <strong>{panel.events.length}</strong>
        </article>
        <article className="eh8s-stat">
          <span className="eh8s-stat-label">With decision</span>
          <strong>
            {panel.events.filter((e) => e.ownerDecisionId != null).length}
          </strong>
        </article>
        <article className="eh8s-stat">
          <span className="eh8s-stat-label">Yellow/Red</span>
          <strong>
            {
              panel.events.filter((e) =>
                ["yellow", "red"].includes((e.semaphore ?? "").toLowerCase()),
              ).length
            }
          </strong>
        </article>
        <article className="eh8s-stat">
          <span className="eh8s-stat-label">Green</span>
          <strong>
            {
              panel.events.filter(
                (e) => (e.semaphore ?? "").toLowerCase() === "green",
              ).length
            }
          </strong>
        </article>
      </div>

      <div className="eh8s-section-head">
        <h2>Event log</h2>
        <p>Newest ticks from agents — decisions escalate to Accounting.</p>
      </div>
      <div className="eh8s-table-wrap">
        <table className="eh8s-table">
          <thead>
            <tr>
              <th>Id</th>
              <th>Agent</th>
              <th>Semaphore</th>
              <th>Type</th>
              <th>Summary</th>
              <th>Decision</th>
            </tr>
          </thead>
          <tbody>
            {panel.events.map((row) => (
              <tr key={row.id}>
                <td>
                  <span className="eh8s-mono">#{row.id}</span>
                </td>
                <td>
                  <span className="eh8s-mono">#{row.opsAgentId}</span>
                </td>
                <td>
                  <span className={`eh8s-badge ${semTone(row.semaphore)}`}>
                    {row.semaphore}
                  </span>
                </td>
                <td>{row.eventType}</td>
                <td>{row.summary}</td>
                <td>
                  {row.ownerDecisionId ? (
                    <span className="eh8s-mono">#{row.ownerDecisionId}</span>
                  ) : (
                    "—"
                  )}
                </td>
              </tr>
            ))}
            {!panel.events.length ? (
              <tr>
                <td colSpan={6} className="eh8s-empty-cell">
                  No agent events yet.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="eh8s-section-head">
        <h2>Console actions</h2>
        <p>Fire a harness tick for any agent code.</p>
      </div>
      <div className="eh8s-action-grid">
        <form className="eh8s-form eh8s-panel" onSubmit={onTick}>
          <h3>Run tick</h3>
          <label>
            Agent code
            <input
              value={agentCode}
              onChange={(ev) => setAgentCode(ev.target.value)}
              required
            />
          </label>
          <label>
            Semaphore
            <select
              value={semaphore}
              onChange={(ev) =>
                setSemaphore(ev.target.value as "green" | "yellow" | "red")
              }
            >
              <option value="green">green</option>
              <option value="yellow">yellow</option>
              <option value="red">red</option>
            </select>
          </label>
          <label>
            Summary
            <input
              value={summary}
              onChange={(ev) => setSummary(ev.target.value)}
              required
            />
          </label>
          <button type="submit">Tick agent</button>
        </form>
      </div>
    </section>
  );
}
