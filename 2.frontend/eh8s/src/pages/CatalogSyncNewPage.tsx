import { FormEvent, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useSyncDeals } from "../hooks/useSyncDeals";

/**
 * Job: propose one sync license deal for this song.
 * Primary: Propose deal.
 * Next: /catalog/tracks/:id/sync/:dealId
 * Hidden: wallet payment, on-chain accounts.
 */
export function CatalogSyncNewPage() {
  const { trackId } = useParams();
  const id = Number(trackId);
  const navigate = useNavigate();
  const sync = useSyncDeals(id);
  const [name, setName] = useState("");
  const [use, setUse] = useState("");
  const [amount, setAmount] = useState("100");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const value = Number(amount);
  const cents = Number.isFinite(value) && value > 0 ? Math.round(value * 100) : 0;
  const fee = Math.floor(cents / 5) / 100;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError("Enter the licensee name.");
      return;
    }
    if (!Number.isFinite(value) || value <= 0 || !/^\d+(\.\d{1,2})?$/.test(amount.trim())) {
      setError("Enter an amount above 0 with at most 2 decimals.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const deal = await sync.create({
        licenseeName: name.trim(),
        useDescription: use.trim() || undefined,
        amountUsdc: value,
      });
      navigate(`/catalog/tracks/${id}/sync/${deal.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not propose the deal");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Sync licensing</p>
      <h1>New sync deal</h1>
      <p className="eh8s-lead">The licensee pays once on DevNet. 80% is credited to the song members.</p>
      {error ? <div className="eh8s-banner bad">{error}</div> : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Licensee
          <input value={name} maxLength={120} onChange={(ev) => setName(ev.target.value)} />
        </label>
        <label>
          Use (optional)
          <input value={use} maxLength={255} onChange={(ev) => setUse(ev.target.value)} />
        </label>
        <label>
          Amount (USDC)
          <input value={amount} onChange={(ev) => setAmount(ev.target.value)} />
        </label>
        <p className="eh8s-muted-line">
          Artists {((cents - Math.floor(cents / 5)) / 100).toFixed(2)} USDC · EH8S {fee.toFixed(2)} USDC
        </p>
        <button type="submit" className="eh8s-btn primary" disabled={saving}>
          {saving ? "Saving." : "Propose deal"}
        </button>
      </form>
      <Link className="eh8s-btn" to={`/catalog/tracks/${id}/sync`}>
        Back to sync deals
      </Link>
    </section>
  );
}
