import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useStageMap } from "../hooks/useStageMap";

/**
 * Job: list a venue with the fields the table requires.
 * Primary: Create venue.
 * Next: book a show.
 */
export function StageVenueNewPage() {
  const stage = useStageMap();
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [capacity, setCapacity] = useState("120");
  const [ticket, setTicket] = useState("15");
  const [contractTypeId, setContractTypeId] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const types = stage.contractTypes;

  useEffect(() => {
    if (!contractTypeId && types[0]) setContractTypeId(String(types[0].id));
  }, [contractTypeId, types]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const cap = Number(capacity);
    const price = Number(ticket);
    const contract = Number(contractTypeId);
    if (!Number.isInteger(cap) || cap < 1 || !Number.isFinite(price) || price < 0 || !contract) {
      setMsg("Capacity, ticket price, and contract type are required.");
      return;
    }
    try {
      await stage.addVenue({
        code: code.trim(),
        name: name.trim(),
        city: "CDMX",
        capacity: cap,
        suggestedTicketUsdc: price,
        contractTypeId: contract,
      });
      navigate("/stage-map/book");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not create venue");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">New venue</p>
        <h1>List a venue</h1>
        <p className="eh8s-lead">Use the venue wallet. Booking is the next screen.</p>
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
          Capacity
          <input required min={1} type="number" value={capacity} onChange={(ev) => setCapacity(ev.target.value)} />
        </label>
        <label>
          Ticket price (USDC)
          <input required min={0} step="0.01" type="number" value={ticket} onChange={(ev) => setTicket(ev.target.value)} />
        </label>
        <label>
          Contract type
          <select required value={contractTypeId} onChange={(ev) => setContractTypeId(ev.target.value)}>
            <option value="">Select.</option>
            {types.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="eh8s-btn primary">
          Create venue
        </button>
      </form>
      <Link className="eh8s-btn" to="/stage-map">
        Back to stage
      </Link>
    </section>
  );
}
