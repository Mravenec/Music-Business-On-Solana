import { useWallet } from "@solana/wallet-adapter-react";
import { useChainConfig } from "../hooks/useChainConfig";
import { activeProgramId } from "../services/chainConfigService";
import { WalletConnectButton as ConnectButton } from "../components/WalletConnectButton";

function shortWallet(pubkey: string): string {
  return `${pubkey.slice(0, 4)}…${pubkey.slice(-4)}`;
}

/**
 * Solana client settings plus Phantom/Solflare connect. Does not require Mainnet funds.
 */
export function SolanaPage() {
  const { loading, error, data } = useChainConfig();
  const { publicKey, connected } = useWallet();
  const programId = data ? activeProgramId(data) : "";

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Solana client</p>
        <h1>Solana config</h1>
        <p className="eh8s-lead">
          MariaDB is the operational store. This page reads BagsCreatorFund-style
          dual-network fields ({data?.envNetworkKey ?? "VITE_NETWORK"},{" "}
          {data?.envRpcKey ?? "VITE_SOLANA_RPC"}) and offers a wallet-adapter connect
          control. No program is deployed from this UI.
        </p>
      </header>

      {loading ? <p className="eh8s-muted-line">Loading chain config…</p> : null}
      {error ? (
        <div className="eh8s-banner bad">Solana config unavailable: {error}</div>
      ) : null}

      {data ? (
        <>
          <div className="eh8s-stats">
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Network</span>
              <strong>{data.network}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">RPC</span>
              <strong>{data.envRpcKey}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Squads</span>
              <strong>{data.squadsMultisig ? "configured" : "not configured"}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Wallet</span>
              <strong>{connected ? "connected" : "not connected"}</strong>
            </article>
          </div>

          <div className="eh8s-section-head">
            <h2>Client identity</h2>
            <p>Dual-network fields read straight from chain_config.</p>
          </div>
          <div className="eh8s-table-wrap">
            <table className="eh8s-table">
              <tbody>
                <tr>
                  <th>Network</th>
                  <td>
                    <span className="eh8s-badge info">{data.network}</span>
                  </td>
                </tr>
                <tr>
                  <th>RPC ({data.envRpcKey})</th>
                  <td>{data.rpcUrl}</td>
                </tr>
                <tr>
                  <th>Program id ({data.envProgramIdDevnetKey})</th>
                  <td>
                    <code className="eh8s-mono">{programId}</code>
                  </td>
                </tr>
                <tr>
                  <th>USDC mint</th>
                  <td>
                    <code className="eh8s-mono">{data.usdcMint}</code>
                  </td>
                </tr>
                <tr>
                  <th>Squads / multisig</th>
                  <td>
                    {data.squadsMultisig ? (
                      <code className="eh8s-mono">{data.squadsMultisig}</code>
                    ) : (
                      <span className="eh8s-muted-line">not configured</span>
                    )}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </>
      ) : null}

      <div className="eh8s-section-head">
        <h2>Connect wallet</h2>
        <p>Phantom or Solflare on DevNet. Connecting does not send funds.</p>
      </div>
      <div className="eh8s-panel">
        <ConnectButton />
        {connected && publicKey ? (
          <p className="eh8s-muted-line" style={{ marginTop: "0.75rem" }}>
            Wallet:{" "}
            <span className="eh8s-badge ok" data-testid="wallet-status">
              <code className="eh8s-mono">{shortWallet(publicKey.toBase58())}</code>
            </span>
          </p>
        ) : (
          <p className="eh8s-muted-line" style={{ marginTop: "0.75rem" }} data-testid="wallet-status">
            Wallet: not connected
          </p>
        )}
      </div>
    </section>
  );
}
