import { useState } from "react";
import { Link } from "react-router-dom";
import { useOwnerTreasury } from "../hooks/useOwnerTreasury";
import { explorerTxUrl } from "../services/settleClaimOnchainTx";

/**
 * Job: move protocol fees from the on-chain treasury to the owner wallet.
 * Primary: Withdraw to my wallet.
 * Next: /owner/treasury/activity
 * Hidden: PDA seeds, instruction bytes, inflow and withdrawal history.
 */
export function OwnerTreasuryPage() {
  const t = useOwnerTreasury();
  const [amount, setAmount] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string; sig?: string } | null>(null);
  const balance = t.treasury ? Number(t.treasury.balanceUsdc) : 0;
  const value = Number(amount);
  const canWithdraw = Number.isFinite(value) && value > 0 && value <= balance && !t.busy;

  async function onWithdraw() {
    setMsg(null);
    try {
      const sig = await t.withdraw(value);
      setMsg({ ok: true, text: `Withdrew ${value.toFixed(2)} USDC to your wallet.`, sig });
      setAmount("");
    } catch (err) {
      setMsg({ ok: false, text: err instanceof Error ? err.message : "Could not withdraw" });
    }
  }

  return (
    <section className="eh8s-page">
      <p className="eh8s-kicker">Studio</p>
      <h1>Treasury</h1>
      <p className="eh8s-lead">
        Protocol fees land in a program-owned account on DevNet. Only the owner wallet can move
        them out.
      </p>
      {!t.walletReady ? (
        <div className="eh8s-banner bad">Connect the owner wallet to open the treasury.</div>
      ) : null}
      {t.loading ? <p className="eh8s-muted-line">Reading the treasury on DevNet…</p> : null}
      {t.error ? <div className="eh8s-banner bad">{t.error}</div> : null}
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
      {t.treasury ? (
        <>
          <p className="eh8s-stat" data-testid="treasury-balance">
            {balance.toFixed(2)} USDC on-chain
          </p>
          {t.treasury.governanceActive ? (
            <div className="eh8s-panel">
              <p>Governance is on: withdrawals need signer approvals.</p>
              <Link className="eh8s-btn primary" to="/owner/governance/new/withdraw">
                Propose a withdrawal
              </Link>
            </div>
          ) : balance > 0 ? (
            <form
              className="eh8s-form eh8s-panel"
              onSubmit={(ev) => {
                ev.preventDefault();
                void onWithdraw();
              }}
            >
              <label>
                Amount (USDC)
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={amount}
                  onChange={(ev) => setAmount(ev.target.value)}
                />
              </label>
              <button type="submit" className="eh8s-btn primary" disabled={!canWithdraw}>
                {t.busy ? "Waiting for wallet…" : "Withdraw to my wallet"}
              </button>
            </form>
          ) : (
            <p className="eh8s-empty">Nothing to withdraw yet — fees arrive with paid plans.</p>
          )}
        </>
      ) : null}
      <div className="eh8s-cta-row">
        {t.treasury ? (
          <Link className="eh8s-btn" to="/owner/treasury/activity">
            Activity
          </Link>
        ) : null}
        <Link className="eh8s-btn" to="/owner">
          Back to inbox
        </Link>
      </div>
    </section>
  );
}
