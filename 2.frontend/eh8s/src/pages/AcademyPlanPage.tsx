import { FormEvent, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useWallet } from "@solana/wallet-adapter-react";
import axios from "axios";
import { useAcademy } from "../hooks/useAcademy";
import { useSession } from "../hooks/useSession";
import { useChainConfig } from "../hooks/useChainConfig";
import { useAcademySubscribeTx } from "../hooks/useOnchainPayments";
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

const PLAN_INCLUDES: Record<string, string[]> = {
  basic: [
    "2 individual classes per week",
    "Access to the platform",
    "Weekly Enigma Score from the teaching agent",
    "Access to class recordings",
  ],
  band: [
    "Everything in Basic",
    "2 group rehearsals per week",
    "Priority to join a band",
    "Eligible for SPP",
  ],
  pro: [
    "Everything in Band",
    "Monthly mentoring from outside the regular class",
    "Access to the recording studio",
    "Music distribution included",
  ],
};

/**
 * Job: see one plan, what it includes, and pay 1-12 months.
 * Primary: Pay with wallet.
 * Next: Active until date or back to list.
 * Hidden: other plans, enroll form, evaluate, raw ids, PDA and plan code.
 */
export function AcademyPlanPage() {
  const { planId } = useParams();
  const academy = useAcademy();
  const { session } = useSession();
  const { data: chain } = useChainConfig();
  const wallet = useWallet();
  const payAcademy = useAcademySubscribeTx();
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [months, setMonths] = useState(1);

  const plan = useMemo(
    () => academy.plans.find((p) => String(p.id) === planId),
    [academy.plans, planId]
  );
  const musicianId = session?.musicianProfile?.id;
  const mine = academy.subscriptions.filter(
    (s) => String(s.academyPlanId) === planId
  );
  const lastPaid = [...mine].reverse().find((s) => s.payerWalletPubkey);
  const expiry = useSubscriptionExpiry("academy", lastPaid?.id);
  const activeUntil = expiry.expiresAt ?? lastPaid?.onchainExpiresAt ?? null;
  const total = plan ? (plan.usdcMonthly * months).toFixed(2) : null;

  async function onSubscribe(e: FormEvent) {
    e.preventDefault();
    if (!musicianId) {
      setMsg("Create a musician profile first.");
      return;
    }
    if (!plan) {
      setMsg("That plan is not available.");
      return;
    }
    if (!wallet.publicKey || !wallet.sendTransaction) {
      setMsg("Connect a wallet before paying.");
      return;
    }
    if (!chain?.programIdDevnet || !chain.usdcMint) {
      setMsg("Network settings are incomplete. Try again later.");
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const created = await academy.subscribe({
        musicianProfileId: musicianId,
        academyPlanId: Number(plan.id),
        months,
      });
      const owner =
        chain.ownerWalletPubkey || "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
      const confirmed = await payAcademy(created.id, owner);
      await academy.refresh();
      await expiry.refresh();
      const paid =
        (confirmed.onChainStatus ?? "").toLowerCase() === "confirmed";
      setMsg(paid ? "Payment confirmed." : "Payment submitted. Checking status…");
    } catch (err) {
      setMsg(axiosMessage(err, "Payment failed"));
    } finally {
      setBusy(false);
    }
  }

  if (!academy.loading && !plan) {
    return (
      <section className="eh8s-page">
        <p className="eh8s-empty">That plan was not found.</p>
        <Link className="eh8s-btn" to="/academy">
          Back to plans
        </Link>
      </section>
    );
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Plan</p>
        <h1>{plan?.name ?? "Plan"}</h1>
        <p className="eh8s-lead">
        {plan?.description || "Monthly academy access."}{" "}
        {plan ? `$${plan.usdcMonthly} USDC per month.` : ""}
      </p>
      </header>
      {plan && PLAN_INCLUDES[plan.code] ? (
        <article className="eh8s-band-card eh8s-plan-includes">
          <h2>What this plan includes</h2>
          <ul>
            {PLAN_INCLUDES[plan.code].map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className="eh8s-muted-line">
            Each subscription creates or renews an on-chain academy subscription.
            85% goes to the EH8S treasury. 15% goes to the instructor wallet.
          </p>
        </article>
      ) : null}
      {msg ? (
        <div
          className={`eh8s-banner ${msg.toLowerCase().includes("fail") ? "bad" : "ok"}`}
        >
          {msg}
        </div>
      ) : null}
      {activeUntil ? (
        <p className="eh8s-muted-line">
          Active until {new Date(activeUntil).toLocaleDateString()}. Paying again adds months.
        </p>
      ) : mine.length > 0 ? (
        <p className="eh8s-muted-line">
          Latest status: {mine[mine.length - 1].onChainStatus ?? "unpaid"}
        </p>
      ) : (
        <p className="eh8s-empty">You have not paid this plan yet.</p>
      )}
      <form className="eh8s-form eh8s-panel" onSubmit={onSubscribe}>
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
          disabled={!musicianId || busy || !wallet.connected}
        >
          {busy ? "Waiting for wallet…" : "Pay with wallet"}
        </button>
      </form>
      <div className="eh8s-cta-row">
        {!musicianId ? (
          <Link className="eh8s-btn" to="/academy/enroll">
            Create musician profile
          </Link>
        ) : null}
        <Link className="eh8s-btn" to="/academy">
          Back to plans
        </Link>
      </div>
    </section>
  );
}
