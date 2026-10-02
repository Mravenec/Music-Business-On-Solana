import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCatalog } from "../hooks/useCatalog";
import { useBands } from "../hooks/useBands";

/**
 * Job: add one catalog track (no SQL mocks).
 * Primary: Create track.
 * Next: that track’s deposit screen.
 */
export function CatalogNewPage() {
  const catalog = useCatalog();
  const bands = useBands();
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [bandId, setBandId] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      const row = await catalog.createTrackRow({
        title: title.trim(),
        provider: "eh8s",
        bandId: Number(bandId) || undefined,
      });
      navigate(`/catalog/tracks/${row.id}/deposit`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not create track");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">New track</p>
        <h1>Add a track</h1>
        <p className="eh8s-lead">Then pay a royalty deposit with DevNet USDC.</p>
      </header>
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Title
          <input required value={title} onChange={(ev) => setTitle(ev.target.value)} />
        </label>
        <label>
          Band
          <select
            required
            value={bandId}
            onChange={(ev) => setBandId(ev.target.value)}
          >
            <option value="">Select…</option>
            {bands.bands.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="eh8s-btn primary">
          Create track
        </button>
      </form>
      <Link className="eh8s-btn eh8s-back" to="/catalog">
        Back to catalog
      </Link>
    </section>
  );
}
