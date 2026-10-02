import { FormEvent, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useAcademy } from "../hooks/useAcademy";
import { useSession } from "../hooks/useSession";
import { useChainConfig } from "../hooks/useChainConfig";
import { useMusicianProfileTx } from "../hooks/useOnchainPayments";

/**
 * Job: create one musician profile.
 * Primary: Save profile.
 * Next: academy plans.
 * Hidden: pay, scores, levels (agent-only), other musicians.
 */
export function AcademyEnrollPage() {
  const academy = useAcademy();
  const { session, refresh: refreshSession } = useSession();
  const { data: chain } = useChainConfig();
  const wallet = useWallet();
  const upsertProfileOnchain = useMusicianProfileTx();
  const [instrumentId, setInstrumentId] = useState("");
  const [country, setCountry] = useState(session?.account?.countryCode ?? "");
  const [msg, setMsg] = useState<string | null>(null);
  const accountId = session?.account?.id;
  const countryOk = /^[A-Z]{3}$/.test(country);

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  async function onEnroll(e: FormEvent) {
    e.preventDefault();
    if (!accountId) {
      setMsg("Connect a wallet first.");
      return;
    }
    try {
      await academy.enrollMusician({
        accountId,
        instrumentId: Number(instrumentId),
        countryCode: country,
      });
      if (wallet.publicKey && chain?.programIdDevnet) {
        await upsertProfileOnchain(chain.programIdDevnet, Number(instrumentId) || 1, country);
      }
      await refreshSession();
      setMsg("Musician profile saved. Sign the wallet prompt for the on-chain PDA.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not save profile");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Profile</p>
        <h1>Create musician profile</h1>
        <p className="eh8s-lead">
        Choose an instrument and your country. Everyone starts at level 0; the NEXUS agent moves
        you up.
      </p>
      </header>
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("could") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onEnroll}>
        <label>
          Instrument
          <select
            required
            value={instrumentId}
            onChange={(ev) => setInstrumentId(ev.target.value)}
          >
            <option value="">Select…</option>
            {academy.instruments.map((row) => (
              <option key={row.id} value={row.id}>
                {row.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Country (3 letters, e.g. MEX)
          <input
            required
            maxLength={3}
            value={country}
            onChange={(ev) => setCountry(ev.target.value.toUpperCase().replace(/[^A-Z]/g, ""))}
            placeholder="MEX"
          />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={!accountId || !countryOk}>
          Save profile
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/academy">
          Back to plans
        </Link>
      </div>
    </section>
  );
}
