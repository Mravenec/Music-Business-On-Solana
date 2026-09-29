import { Link } from "react-router-dom";

/**
 * Job: send the user to a real DevNet pay screen.
 * Primary: Academy tuition.
 * Hidden: intended payment notes (removed).
 */
export function PaymentNewPage() {
  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Pay</p>
      <h1>Pay on-chain</h1>
      <p className="eh8s-lead">
        Payment notes are gone. Every USDC move is a wallet signature on DevNet.
      </p>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/academy">
          Academy tuition
        </Link>
        <Link className="eh8s-btn" to="/catalog">
          Catalog deposit
        </Link>
        <Link className="eh8s-btn" to="/stage-map/settle">
          Settle a concert
        </Link>
      </div>
    </section>
  );
}
