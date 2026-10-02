import { useState } from "react";
import { Link } from "react-router-dom";
import { useOwnerDigest } from "../hooks/useOwnerDigest";

/**
 * Job: read today's AI summary of the platform.
 * Primary: Generate today's digest.
 * Next: back to /owner
 * Hidden: metrics JSON, token counts, model id, Slack delivery details.
 */
export function OwnerDigestPage() {
  const d = useOwnerDigest();
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const latest = d.data?.latest ?? null;

  async function onGenerate() {
    setMsg(null);
    try {
      await d.generate();
      setMsg({ ok: true, text: "Claude wrote today's digest." });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not generate" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio</p>
        <h1>AI digest</h1>
        <p className="eh8s-lead">
        Claude reads today's live numbers — payments, settlements, claims, treasury — and writes a
        short summary with one next step.
      </p>
      </header>
      {!d.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner wallet to open the digest.</div>
      ) : null}
      {d.loading ? <p className="eh8s-muted-line">Loading the latest digest…</p> : null}
      {d.error ? <div className="eh8s-banner bad">{d.error}</div> : null}
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      {d.data && !d.data.aiConfigured ? (
        <p className="eh8s-empty">
          AI is off on this server: set ANTHROPIC_API_KEY for the backend to enable the digest.
        </p>
      ) : null}
      {latest ? (
        <article className="eh8s-score-card">
          <strong>{new Date(latest.createdAt).toLocaleString()}</strong>
          <p style={{ whiteSpace: "pre-wrap" }}>{latest.body}</p>
        </article>
      ) : d.data ? (
        <p className="eh8s-empty">No digest yet.</p>
      ) : null}
      <div className="eh8s-cta-row">
        {d.data?.aiConfigured ? (
          <button
            type="button"
            className="eh8s-btn primary"
            disabled={d.busy}
            onClick={() => void onGenerate()}
          >
            {d.busy ? "Claude is writing…" : "Generate today's digest"}
          </button>
        ) : null}
        <Link className="eh8s-btn" to="/owner">
          Back to inbox
        </Link>
      </div>
    </section>
  );
}
