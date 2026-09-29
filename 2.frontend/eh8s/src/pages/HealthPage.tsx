import { Link } from "react-router-dom";
import { useHealth } from "../hooks/useHealth";

/**
 * Quiet "studio availability" page (footer only) — not a primary marketing nav item.
 * Still proves Vite can reach Spring.
 */
export function HealthPage() {
  const { loading, error, data } = useHealth();

  const ok = Boolean(data) && !error;
  let headline = "Checking the studio…";
  let detail = "One moment while we confirm the application is reachable.";
  if (error) {
    headline = "Studio temporarily unavailable";
    detail =
      "We could not reach the studio servers from this browser. Try again in a minute, or come back later.";
  } else if (data) {
    headline = "Studio is available";
    detail = `${data.product ?? "Enigma H8 Studios"} is running normally. You can connect your wallet and continue.`;
  }

  return (
    <section className="eh8s-status-page">
      <p className="eh8s-kicker">Help</p>
      <h1>Studio availability</h1>
      <p className="eh8s-lead">
        This is a simple check that EH8S can talk to its servers. Most visitors
        never need this page — it lives in the footer for support.
      </p>
      <div
        className={`eh8s-status-card${ok ? " ok" : error ? " bad" : ""}`}
        data-testid="health-status"
      >
        <strong>{headline}</strong>
        <p>{detail}</p>
        {loading ? <p>Refreshing…</p> : null}
      </div>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn primary" to="/">
          Back to home
        </Link>
      </div>
    </section>
  );
}
