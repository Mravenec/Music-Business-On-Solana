import { Link } from "react-router-dom";

/**
 * Job: send settle UX to the one show-settle path — booking escrow on-chain.
 * Primary: Open escrows to settle.
 * Next: VaultSettlePage → Settle from escrow (wallet signs settle_booking).
 * Hidden: MariaDB-only settle; any other invent path.
 */
export function StageSettlePage() {
  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Settlement</p>
        <h1>Settle from escrow</h1>
        <p className="eh8s-lead">
          After the show, the owner or a VAULT agent releases a confirmed booking escrow on-chain —
          expenses to the venue, fee to treasury, pool to the band — then musicians claim.
        </p>
      </header>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/agents/vault">
          Open escrows to settle
        </Link>
        <Link className="eh8s-btn" to="/stage-map/claim">
          Claim royalties
        </Link>
        <Link className="eh8s-btn eh8s-back" to="/stage-map">
          Back to stage
        </Link>
      </div>
    </section>
  );
}
