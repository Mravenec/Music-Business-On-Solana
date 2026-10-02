import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import axios from "axios";
import { useStageMap } from "../hooks/useStageMap";
import { useChainConfig } from "../hooks/useChainConfig";
import { useSettleConcertTx } from "../hooks/useOnchainPayments";
import type { SettleConcertBuild } from "../services/settleClaimOnchainService";

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
 * Job: settle one concert on DevNet (net = gross − expenses; fee → treasury PDA, pool → vault,
 * split to band members by on-chain SPP weight).
 * Primary: Pay with wallet.
 * Next: musician claim screen.
 * Hidden: member accounts and PDAs (shown only as the per-member split after paying).
 */
export function StageSettlePage() {
  const stage = useStageMap();
  const { data: chain } = useChainConfig();
  const wallet = useWallet();
  const settleConcert = useSettleConcertTx();
  const [concertId, setConcertId] = useState(
    stage.concerts[0] ? String(stage.concerts[0].id) : "1"
  );
  const [grossUsdc, setGrossUsdc] = useState("875");
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [split, setSplit] = useState<SettleConcertBuild | null>(null);

  async function onSettle(e: FormEvent) {
    e.preventDefault();
    if (!wallet.publicKey || !wallet.sendTransaction) {
      setMsg("Connect the venue wallet before settling.");
      return;
    }
    if (!chain?.programIdDevnet || !chain.usdcMint || !chain.ownerWalletPubkey) {
      setMsg("Network settings are incomplete (program, USDC mint, owner).");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const settlement = await stage.settle(Number(concertId), Number(grossUsdc));
      const { signature, build } = await settleConcert(settlement.id, {
        usdcMint: chain.usdcMint,
        concertId: settlement.concertId,
      });
      await stage.refresh();
      setSplit(build);
      setMsg(
        `Settled on DevNet (fee→owner, pool split to ${build.members.length} members). ${signature.slice(0, 8)}… Claim next.`,
      );
    } catch (err) {
      setMsg(axiosMessage(err, "Could not settle"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Settlement</p>
        <h1>Settle a concert</h1>
        <p className="eh8s-lead">
        Venue pays DevNet USDC on net (gross − recorded expenses): protocol fee to owner treasury,
        band pool to vault, split across members by the band vault weights.
      </p>
      </header>
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("could") || msg.toLowerCase().includes("Connect") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      <form className="eh8s-form eh8s-panel" onSubmit={onSettle}>
        <label>
          Concert
          <select value={concertId} onChange={(ev) => setConcertId(ev.target.value)}>
            {stage.concerts.map((c) => (
              <option key={c.id} value={c.id}>
                Concert {c.id}
              </option>
            ))}
          </select>
        </label>
        <label>
          Gross (USDC)
          <input value={grossUsdc} onChange={(ev) => setGrossUsdc(ev.target.value)} />
        </label>
        <button
          type="submit"
          className="eh8s-btn primary"
          disabled={busy || !wallet.connected}
        >
          {busy ? "Waiting for wallet…" : "Pay with wallet"}
        </button>
      </form>
      {split ? (
        <div className="eh8s-panel">
          <p>
            Gross {split.grossUsdc} · expenses {split.expensesUsdc} · net {split.netUsdc} · fee{" "}
            {split.eh8sFeeUsdc} · band pool {split.bandPoolUsdc} USDC
          </p>
          <ul>
            {split.members.map((m) => (
              <li key={m.wallet}>
                {m.wallet.slice(0, 4)}…{m.wallet.slice(-4)} — {(m.bps / 100).toFixed(2)}% →{" "}
                {m.pendingUsdc} USDC pending
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/stage-map/claim">
          Claim royalties
        </Link>
        <Link className="eh8s-btn" to="/stage-map">
          Back to stage
        </Link>
      </div>
    </section>
  );
}
