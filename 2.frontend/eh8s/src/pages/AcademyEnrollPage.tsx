import { FormEvent, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import { useAcademy } from "../hooks/useAcademy";
import { useSession } from "../hooks/useSession";
import { useChainConfig } from "../hooks/useChainConfig";
import { useMusicianProfileTx } from "../hooks/useOnchainPayments";
import { formatSolanaTxError } from "../services/solanaTxError";

/**
 * Job: create one musician profile (MariaDB + on-chain PDA).
 * Primary: Save profile.
 * Next: academy plans.
 * Hidden: pay, scores, levels (agent-only), other musicians.
 */
export function AcademyEnrollPage() {
  const academy = useAcademy();
  const { session, reloadFromToken } = useSession();
  const { data: chain } = useChainConfig();
  const wallet = useWallet();
  const upsertProfileOnchain = useMusicianProfileTx();
  const [instrumentId, setInstrumentId] = useState("");
  const [country, setCountry] = useState(session?.account?.countryCode ?? "");
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const accountId = session?.account?.id;
  const countryOk = /^[A-Z]{3}$/.test(country);
  const existing = session?.musicianProfile;

  if (!session?.account) {
    return <Navigate to="/" replace />;
  }

  async function onEnroll(e: FormEvent) {
    e.preventDefault();
    if (!accountId) {
      setMsg({ text: "Connect a wallet first.", ok: false });
      return;
    }
    if (!wallet.publicKey || !chain?.programIdDevnet) {
      setMsg({ text: "Connect a wallet and wait for chain config.", ok: false });
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      let profile = existing ?? null;
      if (!profile?.id) {
        profile = await academy.createMusicianProfile({
          accountId,
          instrumentId: Number(instrumentId),
          countryCode: country,
        });
      }
      if (profile.musicianProfilePda) {
        await reloadFromToken();
        setMsg({ text: "Musician profile already linked on-chain.", ok: true });
        return;
      }
      const walletPubkey = wallet.publicKey.toBase58();
      const { signature, musicianProfilePda } = await upsertProfileOnchain(
        chain.programIdDevnet,
        Number(instrumentId || profile.instrumentId) || 1,
        country || profile.countryCode || "MEX",
      );
      await academy.confirmProfileOnchain(profile.id, {
        walletPubkey,
        txSignature: signature,
        musicianProfilePda,
      });
      await reloadFromToken();
      setMsg({
        text: "Musician profile saved on-chain. PDA is stored in your session.",
        ok: true,
      });
    } catch (err) {
      setMsg({
        text: formatSolanaTxError(err, "Could not save profile"),
        ok: false,
      });
    } finally {
      setBusy(false);
    }
  }

  async function onRecover() {
    if (!existing?.id || !wallet.publicKey) {
      setMsg({ text: "Need an existing profile and connected wallet.", ok: false });
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      await academy.recoverProfileOnchain(existing.id, {
        walletPubkey: wallet.publicKey.toBase58(),
      });
      await reloadFromToken();
      setMsg({
        text: "Recovered on-chain PDA into MariaDB. No new signature was required.",
        ok: true,
      });
    } catch (err) {
      setMsg({
        text: formatSolanaTxError(err, "Could not recover profile PDA"),
        ok: false,
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Profile</p>
        <h1>Create musician profile</h1>
        <p className="eh8s-lead">
          Choose an instrument and your country. Everyone starts at level 0; the NEXUS agent moves
          you up. One wallet signature creates the on-chain PDA; the session reloads without a
          second login prompt.
        </p>
      </header>
      {msg ? (
        <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>{msg.text}</div>
      ) : null}
      {existing?.id && !existing.musicianProfilePda ? (
        <div className="eh8s-panel">
          <p className="eh8s-muted-line">
            Profile #{existing.id} exists in MariaDB without a PDA. If you already signed on
            DevNet, recover it without signing again.
          </p>
          <button
            type="button"
            className="eh8s-btn"
            disabled={busy || !wallet.publicKey}
            onClick={() => void onRecover()}
          >
            Recover on-chain PDA
          </button>
        </div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onEnroll}>
        <label>
          Instrument
          <select
            required
            value={instrumentId || String(existing?.instrumentId ?? "")}
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
        <button
          type="submit"
          className="eh8s-btn primary"
          disabled={!accountId || !countryOk || busy}
        >
          {busy ? "Working…" : "Save profile"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn eh8s-back" to="/academy">
          Back to plans
        </Link>
      </div>
    </section>
  );
}
