import { useEffect } from "react";
import { Link, useParams } from "react-router-dom";
import { useBands } from "../hooks/useBands";

/**
 * Job: see one band.
 * Primary: Add member.
 * Next: /bands/:id/members/add
 * Secondary: Activate band vault / On-chain weights (/bands/:id/vault/*), SPP cycle (/bands/:id/cycle),
 *   Find musicians (/bands/:id/match, HARMONY), Tour routes (/bands/:id/tours, ATLAS).
 * Hidden: other bands, create form, vault ids.
 */
export function BandDetailPage() {
  const { bandId } = useParams();
  const { selectedBandId, setSelectedBandId, bands, members, rehearsals, loading, error } =
    useBands();
  const id = Number(bandId);

  useEffect(() => {
    if (Number.isFinite(id) && selectedBandId !== id) setSelectedBandId(id);
  }, [id, selectedBandId, setSelectedBandId]);

  const band = bands.find((b) => b.id === id);

  if (!loading && !band) {
    return (
      <section className="eh8s-page">
        <p className="eh8s-empty">That band was not found.</p>
        <Link className="eh8s-btn eh8s-back" to="/bands">
          Back to bands
        </Link>
      </section>
    );
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Band</p>
        <h1>{band?.name ?? "Band"}</h1>
        <p className="eh8s-lead">Roster and rehearsals for this ensemble only.</p>
      </header>
      {error ? (
        <div className="eh8s-banner bad">Could not load this band.</div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to={`/bands/${id}/members/add`}>
          Add member
        </Link>
        <Link className="eh8s-btn" to={`/bands/${id}/rehearsals/new`}>
          Schedule rehearsal
        </Link>
        <Link className="eh8s-btn" to={`/bands/${id}/cycle`}>
          SPP cycle
        </Link>
        <Link className="eh8s-btn" to={`/bands/${id}/match`}>
          Find musicians
        </Link>
        <Link className="eh8s-btn" to={`/bands/${id}/tours`}>
          Tour routes
        </Link>
        <Link
          className="eh8s-btn"
          to={band?.bandVaultPda ? `/bands/${id}/vault/sync` : `/bands/${id}/vault/activate`}
        >
          {band?.bandVaultPda ? "On-chain weights" : "Activate band vault"}
        </Link>
      </div>
      <h2>Members</h2>
      {members.length === 0 ? (
        <p className="eh8s-empty">No members yet.</p>
      ) : (
        <ul>
          {members.map((row) => (
            <li key={row.id}>
              {row.roleInBand}
            </li>
          ))}
        </ul>
      )}
      <h2>Rehearsals</h2>
      {rehearsals.length === 0 ? (
        <p className="eh8s-empty">No rehearsals scheduled.</p>
      ) : (
        <ul>
          {rehearsals.map((row) => (
            <li key={row.id}>
              {row.notes || "Rehearsal"}{" "}
              <Link to={`/bands/${id}/rehearsals/${row.id}`}>Check in</Link>{" · "}
              <Link to={`/bands/${id}/rehearsals/${row.id}/rate`}>Rate bandmates</Link>
            </li>
          ))}
        </ul>
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/bands">
          Back to bands
        </Link>
      </div>
    </section>
  );
}
