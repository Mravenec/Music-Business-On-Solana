import { useMemo, useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useStudioPower } from "../hooks/useStudioPower";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: set one partner's on-chain share with a principal signature.
 * Primary: Sign partner share (0% ends the live window).
 * Next: back to /owner/studio-power or partner books.
 * Hidden: studio admin grant/revoke, MariaDB partner POST, treasury.
 */
export function StudioPowerPartnerSharePage() {
  const { session, loading: sessionLoading } = useSession();
  const power = useStudioPower();
  const [params] = useSearchParams();
  const initialWallet = useMemo(() => params.get("wallet") ?? "", [params]);
  const initialBps = useMemo(() => {
    const raw = params.get("bps");
    if (!raw) return "";
    const n = Number(raw);
    return Number.isFinite(n) ? String(n / 100) : "";
  }, [params]);
  const [wallet, setWallet] = useState(initialWallet);
  const [percent, setPercent] = useState(initialBps);
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const principal = Boolean(
    session?.platformOwner || session?.account?.role?.toLowerCase() === "owner",
  );
  const walletOk = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(wallet.trim());
  const shareBps = Math.round(Number(percent) * 100);
  const shareOk = Number.isFinite(shareBps) && shareBps >= 0 && shareBps <= 10000;

  if (sessionLoading) {
    return <p className="eh8s-muted-line">Opening partner share…</p>;
  }
  if (!session?.account) {
    return <Navigate to="/" replace />;
  }
  if (!principal) {
    return <Navigate to="/owner/studio-power" replace />;
  }

  async function onSign() {
    setMsg(null);
    if (!shareOk) {
      setMsg({ ok: false, text: "Enter a share from 0% to 100%." });
      return;
    }
    try {
      const startsAt = Math.floor(Date.now() / 1000);
      const sig = await power.setPartnerShare({
        partnerWalletPubkey: wallet.trim(),
        shareBps,
        startsAt,
        endsAt: 0,
      });
      setMsg({
        ok: true,
        text:
          shareBps === 0
            ? "Partner share ended on DevNet (0 bps)."
            : "Partner share set on DevNet. Books will follow this window.",
        sig,
      });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not set share" });
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Studio power</p>
        <h1>Set partner share</h1>
        <p className="eh8s-lead">
          Sign set_partner_share on DevNet. Use 0% to end a live share. JWT or MariaDB alone cannot
          rewrite an on-chain partner window.
        </p>
      </header>
      {!power.walletReady ? (
        <div className="eh8s-banner bad">Connect the principal wallet to sign.</div>
      ) : null}
      {msg ? (
        <div className={`eh8s-banner ${msg.ok ? "ok" : "bad"}`}>
          {msg.text}{" "}
          {msg.sig ? (
            <a href={explorerTxUrl(msg.sig)} target="_blank" rel="noreferrer">
              View transaction
            </a>
          ) : null}
        </div>
      ) : null}
      <form
        className="eh8s-form eh8s-panel"
        onSubmit={(ev) => {
          ev.preventDefault();
          void onSign();
        }}
      >
        <label>
          Partner wallet
          <input
            value={wallet}
            onChange={(ev) => setWallet(ev.target.value)}
            spellCheck={false}
            placeholder="Base58 partner wallet"
          />
        </label>
        <label>
          Share %
          <input
            value={percent}
            onChange={(ev) => setPercent(ev.target.value)}
            inputMode="decimal"
            placeholder="25"
          />
        </label>
        <button type="submit" className="eh8s-btn primary" disabled={!walletOk || !shareOk || power.busy}>
          {power.busy ? "Waiting for wallet…" : "Sign partner share"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        <Link className="eh8s-btn" to="/studio-ledger">
          Partner books
        </Link>
        <Link className="eh8s-btn eh8s-back" to="/owner/studio-power">
          Back to studio power
        </Link>
      </div>
    </section>
  );
}
