import { Link } from "react-router-dom";
import { useMusicianLevels } from "../hooks/useMusicianLevels";

/**
 * Job: pick one musician whose Enigma level changes.
 * Primary: Open musician.
 * Next: /agents/levels/:musicianProfileId
 * Hidden: level form, profile PDA, instruction bytes.
 */
export function AgentLevelsPage() {
  const lv = useMusicianLevels();
  const role = lv.console?.signerRole === "owner" ? "owner" : "NEXUS agent";

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Enigma</p>
        <h1>Musician levels</h1>
        <p className="eh8s-lead">
          Levels move on-chain only when the owner or the NEXUS agent signs. Musicians never set
          their own.
        </p>
      </header>
      {!lv.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner or NEXUS agent wallet.</div>
      ) : null}
      {lv.loading ? <p className="eh8s-muted-line">Loading musicians…</p> : null}
      {lv.error ? <div className="eh8s-banner bad">{lv.error}</div> : null}
      {lv.console ? <p className="eh8s-muted-line">Signing as {role}.</p> : null}
      <div className="eh8s-band-grid">
        {(lv.console?.musicians ?? []).map((m) => (
          <article key={m.musicianProfileId} className="eh8s-band-card">
            <h3>{m.displayName}</h3>
            <p className="eh8s-band-code">
              Level {m.levelNumber ?? 0} · {m.levelName ?? "Unranked"}
              {m.countryCode ? ` · ${m.countryCode}` : ""}
            </p>
            <Link className="eh8s-btn primary" to={`/agents/levels/${m.musicianProfileId}`}>
              Open musician
            </Link>
          </article>
        ))}
        {lv.console && !lv.console.musicians.length ? (
          <p className="eh8s-empty">No musician has an on-chain wallet yet.</p>
        ) : null}
      </div>
    </section>
  );
}
