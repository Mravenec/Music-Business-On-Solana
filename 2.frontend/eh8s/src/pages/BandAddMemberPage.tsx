import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useBands } from "../hooks/useBands";
import { useSession } from "../hooks/useSession";

/**
 * Job: add one member to this band.
 * Primary: Add member.
 * Next: band detail.
 * Hidden: create band, cycles, check-in.
 */
export function BandAddMemberPage() {
  const { bandId } = useParams();
  const bandsApi = useBands();
  const { session } = useSession();
  const id = Number(bandId);
  const [musicianId, setMusicianId] = useState(
    session?.musicianProfile?.id ? String(session.musicianProfile.id) : ""
  );
  const [roleInBand, setRoleInBand] = useState("member");
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (Number.isFinite(id)) bandsApi.setSelectedBandId(id);
  }, [id, bandsApi.setSelectedBandId]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      await bandsApi.addMember({
        bandId: id,
        musicianProfileId: Number(musicianId),
        roleInBand,
      });
      setMsg("Member added.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not add member");
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Roster</p>
      <h1>Add a member</h1>
      <p className="eh8s-lead">One person, one role, this band only.</p>
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("could") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Musician profile id
          <input
            required
            value={musicianId}
            onChange={(ev) => setMusicianId(ev.target.value)}
          />
        </label>
        <label>
          Role
          <input
            value={roleInBand}
            onChange={(ev) => setRoleInBand(ev.target.value)}
          />
        </label>
        <button type="submit" className="eh8s-btn primary">
          Add member
        </button>
      </form>
      <Link className="eh8s-btn" to={`/bands/${id}`}>
        Back to band
      </Link>
    </section>
  );
}
