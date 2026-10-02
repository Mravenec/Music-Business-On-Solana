import { useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import { useSession } from "../hooks/useSession";
import { useOwnerLedger, useStudioLedger } from "../hooks/useStudioLedger";

const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

const SOURCE_LABEL: Record<string, string> = {
  show_fee: "Show fee",
  academy: "Academy",
  sync: "Sync license",
};

function money(value: number) {
  return `${Number(value || 0).toFixed(2)} USDC`;
}

function joinLabel(startedAt: string | number[] | null | undefined) {
  let year = 0;
  let month = 0;
  let date = 0;
  if (typeof startedAt === "string" && startedAt.length >= 10) {
    [year, month, date] = startedAt.slice(0, 10).split("-").map(Number);
  } else if (Array.isArray(startedAt) && startedAt.length >= 3) {
    [year, month, date] = startedAt;
  }
  if (!year || !month || !date || month < 1 || month > 12) {
    return "";
  }
  return `From ${date} ${MONTHS[month - 1]} ${year}`;
}

function MonthFields({
  month,
  year,
  years,
  onMonth,
  onYear,
}: {
  month: number;
  year: number;
  years: number[];
  onMonth: (value: number) => void;
  onYear: (value: number) => void;
}) {
  return (
    <form className="eh8s-form eh8s-panel eh8s-ledger-filters">
      <label>
        Month
        <select value={month} onChange={(ev) => onMonth(Number(ev.target.value))}>
          {MONTHS.map((name, index) => (
            <option key={name} value={index + 1}>
              {name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Year
        <select value={year} onChange={(ev) => onYear(Number(ev.target.value))}>
          {years.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>
    </form>
  );
}

/**
 * Job: see where this wallet's studio credits came from in one month.
 * Primary: choose the month and year.
 * Next: read each source, or the empty line.
 * Hidden: partner form, other wallets, studio fee totals.
 */
function UserLedger({ year, month, years, onMonth, onYear }: {
  year: number;
  month: number;
  years: number[];
  onMonth: (value: number) => void;
  onYear: (value: number) => void;
}) {
  const ledger = useStudioLedger(year, month);
  const lines = [
    ...ledger.solo.map((row) => ({ key: `solo-${row.id}`, label: "Solo musician", amount: row.amountUsdc })),
    ...ledger.band.map((row) => ({ key: `band-${row.id}`, label: "Band", amount: row.amountUsdc })),
    ...ledger.venue.map((row) => ({ key: `venue-${row.id}`, label: "Venue", amount: row.expensesUsdc })),
    ...ledger.tutor.map((row) => ({ key: `tutor-${row.id}`, label: "Tutor", amount: row.instructorUsdc })),
    ...ledger.partner.map((row) => ({
      key: `partner-${row.id}`,
      label: `Studio partner · ${SOURCE_LABEL[row.sourceCode] || row.sourceCode}`,
      amount: row.amountUsdc,
    })),
  ];
  const total = lines.reduce((sum, row) => sum + Number(row.amount || 0), 0);
  const empty = !ledger.loading && lines.length === 0;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Accounting</p>
        <h1>Your studio earnings</h1>
        <p className="eh8s-lead">
          Each line says where that credit came from: a solo show, a band, a venue, teaching, or
          your own partner share. Other people's books stay hidden.
        </p>
      </header>
      <MonthFields month={month} year={year} years={years} onMonth={onMonth} onYear={onYear} />
      {ledger.loading ? <p className="eh8s-muted-line">Loading studio earnings…</p> : null}
      {ledger.error ? <div className="eh8s-banner bad">{ledger.error}</div> : null}
      {empty && !ledger.error ? (
        <p className="eh8s-empty">
          The studio recorded no earnings for this wallet in {MONTHS[month - 1]} {year}.
        </p>
      ) : null}
      {!ledger.loading && !empty ? (
        <article className="eh8s-band-card eh8s-plan-includes">
          <h2>
            {MONTHS[month - 1]} {year} · {money(total)}
          </h2>
          <ul>
            {lines.map((row) => (
              <li key={row.key}>
                {row.label} · {money(row.amount)}
              </li>
            ))}
          </ul>
        </article>
      ) : null}
    </section>
  );
}

/**
 * Job: add partners and see how this month's studio fees split across their wallets.
 * Primary: Add partner.
 * Next: read each source and each partner's share.
 * Hidden: personal musician, venue, and tutor earnings.
 */
function OwnerLedger({ year, month, years, onMonth, onYear }: {
  year: number;
  month: number;
  years: number[];
  onMonth: (value: number) => void;
  onYear: (value: number) => void;
}) {
  const [reloadKey, setReloadKey] = useState(0);
  const books = useOwnerLedger(year, month, reloadKey);
  const [name, setName] = useState("");
  const [wallet, setWallet] = useState("");
  const [percent, setPercent] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const showTotal = books.shows.reduce((sum, row) => sum + Number(row.eh8sFeeUsdc || 0), 0);
  const academyTotal = books.academy.reduce((sum, row) => sum + Number(row.treasuryUsdc || 0), 0);
  const syncTotal = books.sync.reduce((sum, row) => sum + Number(row.eh8sUsdc || 0), 0);
  const pool = showTotal + academyTotal + syncTotal;
  const assignedBps = books.partners.reduce((sum, row) => sum + Number(row.shareBps || 0), 0);
  const openBps = Math.max(0, 10000 - assignedBps);

  async function onAdd(ev: React.FormEvent) {
    ev.preventDefault();
    const shareBps = Math.round(Number(percent) * 100);
    if (!name.trim() || !wallet.trim() || !Number.isFinite(shareBps) || shareBps < 1) {
      setFormError("Enter a name, a wallet, and a share greater than zero.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await books.savePartner({
        displayName: name.trim(),
        walletPubkey: wallet.trim(),
        shareBps,
      });
      setName("");
      setWallet("");
      setPercent("");
      setReloadKey((value) => value + 1);
    } catch {
      setFormError("Could not add that partner. Check the wallet and that shares stay at or under 100%.");
    } finally {
      setSaving(false);
    }
  }

  async function onRemove(partnerId: number) {
    setFormError(null);
    try {
      await books.deactivatePartner(partnerId);
      setReloadKey((value) => value + 1);
    } catch {
      setFormError("Could not remove that partner.");
    }
  }

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Owner</p>
        <h1>Partner books</h1>
        <p className="eh8s-lead">
          Add each partner's wallet and their share of the studio's recorded fees. A partner
          shares fees recorded on or after the day they are added, through the day they leave.
          This split is the books. It does not send USDC.
        </p>
      </header>
      <form className="eh8s-form eh8s-panel" onSubmit={onAdd}>
        <label>
          Partner name
          <input value={name} onChange={(ev) => setName(ev.target.value)} maxLength={120} />
        </label>
        <label>
          Wallet
          <input value={wallet} onChange={(ev) => setWallet(ev.target.value)} spellCheck={false} />
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
        <button className="eh8s-btn primary" type="submit" disabled={saving}>
          {saving ? "Adding…" : "Add partner"}
        </button>
        {formError ? <div className="eh8s-banner bad">{formError}</div> : null}
      </form>
      {books.partners.length === 0 && !books.loading ? (
        <p className="eh8s-empty">No partners yet. Add a wallet and a share.</p>
      ) : (
        <article className="eh8s-band-card eh8s-plan-includes">
          <h2>Partners · {(assignedBps / 100).toFixed(2)}% assigned</h2>
          <ul>
            {books.partners.map((row) => (
              <li key={row.id}>
                {row.displayName} · {row.walletPubkey.slice(0, 4)}..{row.walletPubkey.slice(-4)} ·{" "}
                {(row.shareBps / 100).toFixed(2)}%
                {joinLabel(row.startedAt) ? ` · ${joinLabel(row.startedAt)}` : ""}{" "}
                <button className="eh8s-btn" type="button" onClick={() => onRemove(row.id)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
          {openBps > 0 ? <p>{(openBps / 100).toFixed(2)}% is not assigned to a partner.</p> : null}
        </article>
      )}
      <MonthFields month={month} year={year} years={years} onMonth={onMonth} onYear={onYear} />
      {books.loading ? <p className="eh8s-muted-line">Loading the partner books…</p> : null}
      {books.error ? <div className="eh8s-banner bad">{books.error}</div> : null}
      {!books.loading && !books.error ? (
        <article className="eh8s-band-card eh8s-plan-includes">
          <h2>
            {MONTHS[month - 1]} {year} · {money(pool)}
          </h2>
          <ul>
            <li>Show fees · {money(showTotal)}</li>
            <li>Academy · {money(academyTotal)}</li>
            <li>Sync licenses · {money(syncTotal)}</li>
            {books.partners.map((partner) => {
              const slice = books.allocations
                .filter((row) => row.studioPartnerId === partner.id)
                .reduce((sum, row) => sum + Number(row.amountUsdc || 0), 0);
              return (
                <li key={`slice-${partner.id}`}>
                  {partner.displayName} · {money(slice)}
                </li>
              );
            })}
            {(() => {
              const activeIds = new Set(books.partners.map((partner) => partner.id));
              const left = books.allocations
                .filter((row) => !activeIds.has(row.studioPartnerId))
                .reduce((sum, row) => sum + Number(row.amountUsdc || 0), 0);
              const recorded = books.allocations.reduce(
                (sum, row) => sum + Number(row.amountUsdc || 0),
                0,
              );
              const unassigned = Math.max(0, pool - recorded);
              return (
                <>
                  {left >= 0.005 ? <li>Partners who have left · {money(left)}</li> : null}
                  {unassigned >= 0.005 ? <li>Not assigned · {money(unassigned)}</li> : null}
                </>
              );
            })()}
          </ul>
        </article>
      ) : null}
    </section>
  );
}

/**
 * Picks the owner books or the personal earnings page. The two layouts are not the same.
 */
export function StudioLedgerPage() {
  const { session, loading } = useSession();
  const today = useMemo(() => new Date(), []);
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth() + 1);
  const years = useMemo(() => {
    const current = today.getFullYear();
    return [current, current - 1, current - 2, current - 3, current - 4];
  }, [today]);

  if (loading) {
    return <p className="eh8s-muted-line">Opening your studio…</p>;
  }
  if (!session?.account) {
    return <Navigate to="/" replace />;
  }
  const owner =
    session.platformOwner || session.account.role?.toLowerCase() === "owner";
  if (owner) {
    return (
      <OwnerLedger
        year={year}
        month={month}
        years={years}
        onMonth={setMonth}
        onYear={setYear}
      />
    );
  }
  return (
    <UserLedger year={year} month={month} years={years} onMonth={setMonth} onYear={setYear} />
  );
}
