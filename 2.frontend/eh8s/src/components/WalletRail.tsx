import { Link } from "react-router-dom";
import { useWalletContents } from "../hooks/useWalletContents";

function amount(value: number | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return value.toLocaleString(undefined, { maximumFractionDigits: 4 });
}

/**
 * Small card on the right of a signed-in screen. Shows what the wallet holds.
 */
export function WalletRail() {
  const wallet = useWalletContents();

  return (
    <aside className="eh8s-wallet-rail" aria-label="Wallet">
      <p className="eh8s-kicker">Wallet</p>
      {wallet.loading ? <p className="eh8s-muted-line">Reading wallet…</p> : null}
      {wallet.error ? <p className="eh8s-muted-line">{wallet.error}</p> : null}
      {wallet.data ? (
        <ul className="eh8s-wallet-lines">
          <li>
            <span>SOL</span>
            <strong>{amount(wallet.data.sol)}</strong>
          </li>
          <li>
            <span>USDC</span>
            <strong>{amount(wallet.data.usdc)}</strong>
          </li>
        </ul>
      ) : null}
      <Link className="eh8s-btn" to="/studio-ledger">
        Studio accounting
      </Link>
    </aside>
  );
}
