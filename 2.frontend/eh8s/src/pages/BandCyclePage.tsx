import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useBands } from "../hooks/useBands";

/**
 * Job: see how this band's current SPP cycle splits income.
 * Primary: Close cycle (open cycle) or Open new cycle (no open cycle).
 * Next: stays here with the computed split.
 * Hidden: vault sync, older cycles, raw check-in rows.
 */
export function BandCyclePage() {
  const { bandId } = useParams();
  const id = Number(bandId);
  const bandsApi = useBands();
  const { selectedBandId, setSelectedBandId, cycles, scores, variables } = bandsApi;
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (Number.isFinite(id) && selectedBandId !== id) setSelectedBandId(id);
  }, [id, selectedBandId, setSelectedBandId]);

  const open = cycles.find((c) => c.status === "open") ?? null;
  const shown = open ?? cycles[cycles.length - 1] ?? null;
  const weight = (code: string) =>
    (variables.find((v) => v.code === code)?.weightBps ?? 0) / 100;

  async function onPrimary() {
    setBusy(true);
    try {
      if (open) {
        await bandsApi.closeSppCycle(open.id);
        setMsg({ ok: true, text: "Cycle closed. Shares were computed from real inputs." });
      } else {
        const d = new Date();
        const code = `C${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
        await bandsApi.openSppCycle({ bandId: id, code });
        setMsg({ ok: true, text: `Cycle ${code} opened. Starting Enigma levels were captured.` });
      }
    } catch {
      setMsg({ ok: false, text: open ? "Could not close the cycle." : "Could not open a cycle." });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Shared Participation</p>
      <h1>{shown ? `Cycle ${shown.code}` : "SPP cycle"}</h1>
      <p className="eh8s-lead">
        Attendance {weight("attendance")}% · Punctuality {weight("punctuality")}% · Creative{" "}
        {weight("creative")}% · Skill {weight("skill")}% · Concert {weight("concert")}%
      </p>
      {msg ? <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div> : null}
      <button type="button" className="eh8s-btn primary" disabled={busy} onClick={onPrimary}>
        {busy ? "Working…" : open ? "Close cycle" : "Open new cycle"}
      </button>
      {shown ? (
        <div className="eh8s-table-wrap">
        <table className="eh8s-table">
          <thead>
            <tr>
              <th>Musician</th>
              <th>Attendance</th>
              <th>Punctuality</th>
              <th>Creative</th>
              <th>Skill</th>
              <th>Concert</th>
              <th>Share</th>
            </tr>
          </thead>
          <tbody>
            {scores.map((s) => (
              <tr key={s.id}>
                <td>#{s.musicianProfileId}</td>
                <td>{s.attendancePoints}</td>
                <td>{s.punctualityPoints}</td>
                <td>{s.creativePoints}</td>
                <td>{s.skillPoints}</td>
                <td>{s.concertPoints}</td>
                <td>{(s.shareBps / 100).toFixed(2)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      ) : (
        <p className="eh8s-empty">No cycle yet.</p>
      )}
      <Link className="eh8s-btn" to={`/bands/${id}`}>
        Back to band
      </Link>
    </section>
  );
}
