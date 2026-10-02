import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useLesson } from "../hooks/useCourses";
import { useNexus } from "../hooks/useNexus";
import { useSession } from "../hooks/useSession";

const RUBRIC_LABELS: Record<string, string> = {
  technique: "Technique",
  timing: "Timing",
  tone: "Tone",
  expression: "Expression",
  theory: "Theory",
};

/**
 * Job: ask NEXUS for one Score Enigma.
 * Primary: Ask NEXUS.
 * Next: the stored evaluation (/academy/nexus/:id).
 * With ?lessonId= the lesson's practice exercise is shown and sent with the request.
 * Hidden: history, level signing, prompt text.
 */
export function NexusRequestPage() {
  const { session } = useSession();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const musicianId = Number(params.get("musician")) || session?.musicianProfile?.id || null;
  const lessonId = Number(params.get("lessonId")) || null;
  const practice = useLesson(lessonId ?? NaN).view;
  const nexus = useNexus(null);
  const keys = nexus.status?.rubricKeys ?? Object.keys(RUBRIC_LABELS);
  const [rubric, setRubric] = useState<Record<string, string>>({});
  const [recordingUrl, setRecordingUrl] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!musicianId) {
      setMsg("No musician profile to score yet.");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const out = await nexus.request({
        musicianProfileId: musicianId,
        recordingUrl: recordingUrl.trim() || undefined,
        rubricJson: JSON.stringify(Object.fromEntries(keys.map((k) => [k, Number(rubric[k] ?? "5")]))),
        notes: notes.trim() || undefined,
        lessonId: lessonId ?? undefined,
      });
      navigate(`/academy/nexus/${out.evaluation.id}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not request the Score Enigma.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">NEXUS</p>
        <h1>Request Score Enigma</h1>
        <p className="eh8s-lead">Rate each area from 0 to 10, add a link and notes, then ask NEXUS.</p>
      </header>
      {nexus.status ? <p className="eh8s-muted-line">{nexus.status.audioNote}</p> : null}
      {nexus.status && !nexus.status.aiConfigured ? (
        <div className="eh8s-banner bad">NEXUS is offline: the server has no Anthropic API key.</div>
      ) : null}
      {practice?.lesson.practicePrompt ? (
        <div className="eh8s-banner ok">
          Practice from “{practice.lesson.title}”: {practice.lesson.practicePrompt}
        </div>
      ) : null}
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        {keys.map((k) => (
          <label key={k}>
            {RUBRIC_LABELS[k] ?? k} (0-10)
            <input
              type="number"
              min={0}
              max={10}
              required
              value={rubric[k] ?? "5"}
              onChange={(ev) => setRubric((r) => ({ ...r, [k]: ev.target.value }))}
            />
          </label>
        ))}
        <label>
          Recording link (optional)
          <input
            type="url"
            placeholder="https://…"
            maxLength={400}
            value={recordingUrl}
            onChange={(ev) => setRecordingUrl(ev.target.value)}
          />
        </label>
        <label>
          Notes (optional)
          <textarea maxLength={500} value={notes} onChange={(ev) => setNotes(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={busy || !musicianId}>
          {busy ? "NEXUS is scoring…" : "Ask NEXUS"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to={musicianId ? `/academy/nexus?musician=${musicianId}` : "/academy/nexus"}>
          Back to scores
        </Link>
      </div>
    </section>
  );
}
