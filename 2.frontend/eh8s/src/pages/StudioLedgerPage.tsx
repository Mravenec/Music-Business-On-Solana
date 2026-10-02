import { useMemo, useState } from "react";
import { useStudioLedger } from "../hooks/useStudioLedger";

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

/**
 * Job: see what this studio recorded for the signed-in wallet in one month.
 * Primary: choose the month and year.
 * Next: read each credit, or the empty line.
 * Hidden: wallet balance, payments this person sent, other people's wallets.
 */
export function StudioLedgerPage() {
  const today = useMemo(() => new Date(), []);
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth() + 1);
  const ledger = useStudioLedger(year, month);
  const years = useMemo(() => {
    const current = today.getFullYear();
    return [current, current - 1, current - 2, current - 3, current - 4];
  }, [today]);
  const total =
    ledger.claims.reduce((sum, row) => sum + Number(row.amountUsdc || 0), 0) +
    ledger.shares.reduce((sum, row) => sum + Number(row.instructorUsdc || 0), 0);
  const empty = !ledger.loading && ledger.claims.length === 0 && ledger.shares.length === 0;

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Accounting</p>
        <h1>Studio accounting</h1>
        <p className="eh8s-lead">
          Only what this studio recorded for your wallet. Other funds the wallet holds are not
          listed here.
        </p>
      </header>
      <form className="eh8s-form eh8s-panel eh8s-ledger-filters">
        <label>
          Month
          <select value={month} onChange={(ev) => setMonth(Number(ev.target.value))}>
            {MONTHS.map((name, index) => (
              <option key={name} value={index + 1}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Year
          <select value={year} onChange={(ev) => setYear(Number(ev.target.value))}>
            {years.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
      </form>
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
            {MONTHS[month - 1]} {year} · {total.toFixed(2)} USDC
          </h2>
          <ul>
            {ledger.claims.map((row) => (
              <li key={`claim-${row.id}`}>Royalty claim · {Number(row.amountUsdc).toFixed(2)} USDC</li>
            ))}
            {ledger.shares.map((row) => (
              <li key={`share-${row.id}`}>
                Instructor share · {Number(row.instructorUsdc).toFixed(2)} USDC
              </li>
            ))}
          </ul>
        </article>
      ) : null}
    </section>
  );
}
