import { FormEvent, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import axios from "axios";
import { useCatalog } from "../hooks/useCatalog";
import { useBands } from "../hooks/useBands";
import { useChainConfig } from "../hooks/useChainConfig";
import { useGeoSubscribeTx } from "../hooks/useOnchainPayments";
import { useSubscriptionExpiry } from "../hooks/useSubscriptionExpiry";

function axiosMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data;
    if (typeof data === "string" && data.trim()) return data;
    if (data && typeof data === "object" && "message" in data) {
      return String((data as { message: string }).message);
    }
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

/**
 * Job: pay one zone (region + tier) for 1-12 months on DevNet.
 * Primary: Pay with wallet.
 * Next: Active until date for that zone, or back to catalog.
 * Hidden: geo code seed, PDA, instruction bytes, other bands.
 */
export function CatalogReachPage() {
  const catalog = useCatalog();
  const bands = useBands();
  const { data: chain } = useChainConfig();
  const wallet = useWallet();
  const payZone = useGeoSubscribeTx();
  const [regionId, setRegionId] = useState("");
  const [tierId, setTierId] = useState("");
  const [months, setMonths] = useState(1);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const regions = catalog.geoRegions.filter((r) => r.onchainGeoCode);
  const tier = catalog.geoTiers.find((t) => String(t.id) === tierId);
  const total = tier ? (tier.usdcMonthly * months).toFixed(2) : null;
  const lastPaid = useMemo(
    () =>
      [...catalog.geoSubs]
        .reverse()
        .find(
          (s) =>
            s.payerWalletPubkey &&
            String(s.geoRegionId) === regionId &&
            String(s.geoTierId) === tierId,
        ),
    [catalog.geoSubs, regionId, tierId],
  );
  const expiry = useSubscriptionExpiry("geo", lastPaid?.id);
  const activeUntil = expiry.expiresAt ?? lastPaid?.onchainExpiresAt ?? null;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!wallet.publicKey || !wallet.sendTransaction) {
      setMsg("Connect a wallet before paying.");
      return;
    }
    if (!chain?.programIdDevnet || !chain.usdcMint) {
      setMsg("Network settings are incomplete.");
      return;
    }
    const bandId = bands.bands[0]?.id;
    if (!bandId) {
      setMsg("Create a band first.");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const created = await catalog.subscribeGeo({
        bandId,
        geoTierId: Number(tierId),
        geoRegionId: Number(regionId),
        months,
      });
      const signature = await payZone(created.id, created.geoCode);
      await catalog.refresh();
      await expiry.refresh();
      setMsg(`Zone paid. ${signature.slice(0, 8)}.`);
    } catch (err) {
      setMsg(axiosMessage(err, "Subscribe failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Reach</p>
        <h1>Zone access</h1>
        <p className="eh8s-lead">Pay DevNet USDC per zone. Royalty deposits stay on the track.</p>
      </header>
      {msg ? (
        <div
          className={`eh8s-banner ${/fail|connect|missing/i.test(msg) ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      {activeUntil ? (
        <p className="eh8s-muted-line">
          Active until {new Date(activeUntil).toLocaleDateString()}. Paying again adds months.
        </p>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubmit}>
        <label>
          Region
          <select required value={regionId} onChange={(ev) => setRegionId(ev.target.value)}>
            <option value="">Select.</option>
            {regions.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Reach
          <select required value={tierId} onChange={(ev) => setTierId(ev.target.value)}>
            <option value="">Select.</option>
            {catalog.geoTiers.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name} · ${t.usdcMonthly}/month
              </option>
            ))}
          </select>
        </label>
        <label>
          Months
          <select value={months} onChange={(ev) => setMonths(Number(ev.target.value))}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>
        {total ? <p className="eh8s-muted-line">Total {total} USDC</p> : null}
        <button
          type="submit"
          className="eh8s-btn primary"
          disabled={busy || !wallet.connected || !regionId || !tierId}
        >
          {busy ? "Waiting for wallet." : "Pay with wallet"}
        </button>
      </form>
      <Link className="eh8s-btn" to="/catalog">
        Back to catalog
      </Link>
    </section>
  );
}
