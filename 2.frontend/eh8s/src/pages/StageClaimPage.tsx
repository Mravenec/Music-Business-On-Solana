import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import axios from "axios";
import { useStageMap } from "../hooks/useStageMap";
import { useChainConfig } from "../hooks/useChainConfig";
import { useClaimRoyaltiesTx } from "../hooks/useOnchainPayments";

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
 * Job: claim one royalty payout to the musician wallet.
 * Primary: Pay with wallet.
 */
export function StageClaimPage() {
  const stage = useStageMap();
  const { data: chain } = useChainConfig();
  const wallet = useWallet();
  const claimRoyalties = useClaimRoyaltiesTx();
  const [claimId, setClaimId] = useState(
    stage.claims[0] ? String(stage.claims[0].id) : ""
  );
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onClaim(e: FormEvent) {
    e.preventDefault();
    if (!wallet.publicKey || !wallet.sendTransaction) {
      setMsg("Connect the musician wallet before claiming.");
      return;
    }
    if (!chain?.programIdDevnet || !chain.usdcMint) {
      setMsg("Network settings are incomplete.");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const signature = await claimRoyalties(Number(claimId), chain.programIdDevnet, chain.usdcMint);
      await stage.refresh();
      setMsg(`Claim confirmed. ${signature.slice(0, 8)}…`);
    } catch (err) {
      setMsg(axiosMessage(err, "Claim failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Claim</p>
      <h1>Claim royalties</h1>
      <p className="eh8s-lead">Musician wallet receives DevNet USDC. No mark-claimed shortcut.</p>
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("fail") || msg.toLowerCase().includes("Connect") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      {stage.claims.length === 0 ? (
        <p className="eh8s-empty">No pending claims. Settle a concert first.</p>
      ) : (
        <form className="eh8s-form eh8s-panel" onSubmit={onClaim}>
          <label>
            Claim
            <select value={claimId} onChange={(ev) => setClaimId(ev.target.value)}>
              {stage.claims.map((c) => (
                <option key={c.id} value={c.id}>
                  Claim {c.id} · ${c.amountUsdc}
                </option>
              ))}
            </select>
          </label>
          <button
            type="submit"
            className="eh8s-btn primary"
            disabled={busy || !wallet.connected}
          >
            {busy ? "Waiting for wallet…" : "Pay with wallet"}
          </button>
        </form>
      )}
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/catalog/credits">
          Song credits
        </Link>
        <Link className="eh8s-btn" to="/stage-map">
          Back to stage
        </Link>
      </div>
    </section>
  );
}
