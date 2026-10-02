import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMusicianLevels } from "../hooks/useMusicianLevels";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: move one musician to a new Enigma level on-chain.
 * Primary: Sign level change.
 * Next: back to /agents/levels
 * Hidden: profile PDA, AgentAuthority account, other musicians.
 */
export function AgentLevelSetPage() {
  const { musicianProfileId = "" } = useParams();
  const id = Number(musicianProfileId);
  const lv = useMusicianLevels();
  const target = lv.console?.musicians.find((m) => m.musicianProfileId === id) ?? null;
  const [level, setLevel] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);

  useEffect(() => {
    if (target) setLevel(String(target.levelNumber ?? 0));
  }, [target]);

  const max = lv.console?.maxLevel ?? 5;
  const changed = target !== null && level !== "" && Number(level) !== (target.levelNumber ?? 0);

  async function onSign() {
    setMsg(null);
    try {
      const sig = await lv.setLevel(id, Number(level));
      setMsg({ ok: true, text: `Level ${level} recorded on DevNet.`, sig });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not change the level" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Enigma level</p>
        <h1>{target?.displayName ?? "Musician"}</h1>
      </header>
      {target ? (
        <p className="eh8s-lead">
          Now at level {target.levelNumber ?? 0} · {target.levelName ?? "Unranked"}.
        </p>
      ) : null}
      {lv.loading ? <p className="eh8s-muted-line">Loading musician…</p> : null}
      {lv.error ? <div className="eh8s-banner bad">{lv.error}</div> : null}
      {msg ? (
        <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>
          {msg.text}{" "}
          {msg.sig ? (
            <a href={explorerTxUrl(msg.sig)} target="_blank" rel="noreferrer">
              View transaction
            </a>
          ) : null}
        </div>
      ) : null}
      {target ? (
        <form
          className="eh8s-form eh8s-panel"
          onSubmit={(ev) => {
            ev.preventDefault();
            void onSign();
          }}
        >
          <label>
            New level
            <select value={level} onChange={(ev) => setLevel(ev.target.value)}>
              {Array.from({ length: max + 1 }, (_, n) => (
                <option key={n} value={n}>
                  Level {n}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="eh8s-btn primary" disabled={!changed || lv.busy}>
            {lv.busy ? "Waiting for wallet…" : "Sign level change"}
          </button>
        </form>
      ) : null}
      {lv.console && !target ? <p className="eh8s-empty">Unknown musician.</p> : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to={`/academy/nexus?musician=${id}`}>
          Score Enigma history
        </Link>
        <Link className="eh8s-btn" to="/agents/levels">
          Back to musicians
        </Link>
      </div>
    </section>
  );
}
