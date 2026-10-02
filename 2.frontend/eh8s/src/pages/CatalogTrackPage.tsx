import { Link, useParams } from "react-router-dom";
import { useCatalog } from "../hooks/useCatalog";
import { useSongPool } from "../hooks/useSongPool";

/**
 * Job: see one track and its royalty pool state.
 * Primary: Activate song pool (until active), then Deposit royalties.
 * Next: /catalog/tracks/:id/pool · /catalog/tracks/:id/deposit · /catalog/tracks/:id/sync
 * Hidden: split details, PDA, geo, channel, claims.
 */
export function CatalogTrackPage() {
  const { trackId } = useParams();
  const id = Number(trackId);
  const catalog = useCatalog();
  const song = useSongPool(id);
  const track = catalog.tracks.find((t) => t.id === id);
  const active = Boolean(song.pool?.active);

  if (!catalog.loading && !track) {
    return (
      <section className="eh8s-page">
        <p className="eh8s-empty">That track was not found.</p>
        <Link className="eh8s-btn eh8s-back" to="/catalog">
          Back to catalog
        </Link>
      </section>
    );
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Track</p>
        <h1>{track?.title ?? "Track"}</h1>
        <p className="eh8s-lead">{track?.provider}</p>
      </header>
      <p className="eh8s-muted-line">
        {song.loading
          ? "Reading song pool…"
          : active
            ? `Song pool active on DevNet · ${song.pool?.splits?.length ?? 0} member(s)`
            : "Song pool not on-chain yet"}
      </p>
      {active ? (
        <Link className="eh8s-btn primary" to={`/catalog/tracks/${id}/deposit`}>
          Deposit royalties
        </Link>
      ) : (
        <Link className="eh8s-btn primary" to={`/catalog/tracks/${id}/pool`}>
          Activate song pool
        </Link>
      )}
      <div className="eh8s-cta-row">
        {active ? (
          <Link className="eh8s-btn" to={`/catalog/tracks/${id}/pool`}>
            Song splits
          </Link>
        ) : null}
        <Link className="eh8s-btn" to={`/catalog/tracks/${id}/sync`}>
          Sync licenses
        </Link>
        <Link className="eh8s-btn eh8s-back" to="/catalog">
          Back to catalog
        </Link>
      </div>
    </section>
  );
}
