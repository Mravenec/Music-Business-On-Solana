import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useBands } from "../hooks/useBands";

/**
 * Job: create one band.
 * Primary: Create.
 * Next: that band’s page.
 * Hidden: members, rehearsals, cycles.
 */
export function BandNewPage() {
  const bandsApi = useBands();
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [geoCode, setGeoCode] = useState("MEX");
  const [msg, setMsg] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      const row = await bandsApi.createBandRow({
        code: code.trim(),
        name: name.trim(),
        bandType: "product",
        sppEnabled: 1,
        geoCode: geoCode.trim() || undefined,
      });
      navigate(`/bands/${row.id}`);
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not create band");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">New band</p>
        <h1>Create a band</h1>
        <p className="eh8s-lead">You join as the leader. Other members come next.</p>
      </header>
      {msg ? <div className="eh8s-banner bad">{msg}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Code
          <input required value={code} onChange={(ev) => setCode(ev.target.value)} />
        </label>
        <label>
          Name
          <input required value={name} onChange={(ev) => setName(ev.target.value)} />
        </label>
        <label>
          Region
          <input value={geoCode} onChange={(ev) => setGeoCode(ev.target.value)} />
        </label>
        <button type="submit" className="eh8s-btn primary">
          Create band
        </button>
      </form>
      <Link className="eh8s-btn" to="/bands">
        Back to bands
      </Link>
    </section>
  );
}
