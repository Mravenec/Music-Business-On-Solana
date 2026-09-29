import { Link } from "react-router-dom";
import { useAgentAuthority } from "../hooks/useAgentAuthority";

function bitNames(mask: number | null | undefined, bits: { mask: number; name: string }[]): string {
  const names = bits.filter((b) => ((mask ?? 0) & b.mask) !== 0).map((b) => b.name);
  return names.length ? names.join(" · ") : "no on-chain power";
}

/**
 * Job: pick one agent whose on-chain powers the owner wants to change.
 * Primary: Open agent.
 * Next: /owner/agents/:code
 * Hidden: permission checkboxes, PDA, instruction bytes, grant history.
 */
export function OwnerAgentsPage() {
  const a = useAgentAuthority();
  const bits = a.roster?.permissionBits ?? [];

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio</p>
        <h1>Agent powers</h1>
        <p className="eh8s-lead">
          Agents only act on-chain with the bits you sign. You keep every power yourself.
        </p>
      </header>
      {!a.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner wallet to manage agents.</div>
      ) : null}
      {a.loading ? <p className="eh8s-muted-line">Loading agents…</p> : null}
      {a.error ? <div className="eh8s-banner bad">{a.error}</div> : null}
      {a.roster?.governanceActive ? (
        <div className="eh8s-banner ok">
          Governance is on: agent powers change through a{" "}
          <Link to="/owner/governance/new/authorize_agent">signer proposal</Link>.
        </div>
      ) : null}
      <div className="eh8s-band-grid">
        {(a.roster?.agents ?? []).map((agent) => {
          const latest = a.latestFor(agent.code);
          return (
            <article key={agent.id} className="eh8s-band-card" data-testid={`agent-${agent.code}`}>
              <h3>{agent.name}</h3>
              <p className="eh8s-band-code">{agent.roleSummary}</p>
              <p className="eh8s-muted-line">
                {latest ? bitNames(latest.permissions, bits) : "not authorized on-chain"}
              </p>
              <Link className="eh8s-btn primary" to={`/owner/agents/${agent.code}`}>
                Open agent
              </Link>
            </article>
          );
        })}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/owner">
          Back to inbox
        </Link>
      </div>
    </section>
  );
}
