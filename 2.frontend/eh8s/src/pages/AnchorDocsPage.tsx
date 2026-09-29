import { useAnchorMetadata } from "../hooks/useAnchorMetadata";

/**
 * Live DevNet Anchor program status — instructions and deploy smoke notes.
 */
export function AnchorDocsPage() {
  const { loading, error, data } = useAnchorMetadata();
  const instructions = Array.isArray(data?.instructions)
    ? (data.instructions as string[])
    : [];

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Solana DevNet</p>
        <h1>Programs</h1>
        <p className="eh8s-lead">
          Live metadata for the EH8S Anchor program. Deploy with Solana CLI, then
          wallet flows use DevNet USDC — not Mainnet and not fake markers.
        </p>
      </header>

      {loading ? <p className="eh8s-muted-line">Loading program metadata…</p> : null}
      {error ? (
        <div className="eh8s-banner bad">Anchor metadata unavailable: {error}</div>
      ) : null}

      {data ? (
        <>
          <div className="eh8s-stats">
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Network</span>
              <strong>{data.network}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">IDL</span>
              <strong>{data.anchorIdlVersion ?? "—"}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Instructions</span>
              <strong>{instructions.length}</strong>
            </article>
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Mainnet</span>
              <strong>{String(data.mainnetDeployRequired ?? false)}</strong>
            </article>
          </div>

          <div className="eh8s-section-head">
            <h2>Program identity</h2>
            <p>Must match `anchor deploy` output and MariaDB chain_config.</p>
          </div>
          <div className="eh8s-table-wrap">
            <table className="eh8s-table">
              <tbody>
                <tr>
                  <th>Program id (DevNet)</th>
                  <td>
                    <code className="eh8s-mono">{data.programIdDevnet}</code>
                  </td>
                </tr>
                <tr>
                  <th>RPC</th>
                  <td>{data.rpcUrl}</td>
                </tr>
                <tr>
                  <th>USDC mint (DevNet)</th>
                  <td>
                    <code className="eh8s-mono">{data.usdcMint}</code>
                  </td>
                </tr>
                <tr>
                  <th>Source path</th>
                  <td>
                    <code>{data.anchorScaffoldPath ?? "4.anchor/eh8s-devnet"}</code>
                  </td>
                </tr>
                <tr>
                  <th>Owner wallet</th>
                  <td>
                    <code className="eh8s-mono">
                      {String(data.ownerWalletPubkey ?? "—")}
                    </code>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div className="eh8s-section-head">
            <h2>Instruction registry</h2>
            <p>On-chain entrypoints the product must call with a real wallet signature.</p>
          </div>
          <div className="eh8s-band-grid">
            {instructions.map((ix) => (
              <article
                key={ix}
                className="eh8s-band-card"
                style={{ cursor: "default" }}
              >
                <div className="eh8s-band-card-top">
                  <span className="eh8s-badge ok">ix</span>
                </div>
                <h3>
                  <code>{ix}</code>
                </h3>
              </article>
            ))}
          </div>

          {data.note ? <div className="eh8s-banner ok">{data.note}</div> : null}
        </>
      ) : null}

      <div className="eh8s-section-head">
        <h2>Deploy smoke (engineer)</h2>
        <p>Requires Solana CLI + funded DevNet wallet on this machine.</p>
      </div>
      <div className="eh8s-panel">
        <pre>{`cd 4.anchor/eh8s-devnet
solana config set --url https://api.devnet.solana.com
solana airdrop 2
anchor build --ignore-keys
anchor deploy --provider.cluster devnet
# then UPDATE chain_config SET program_deployed_at = UTC_TIMESTAMP()`}</pre>
      </div>
    </section>
  );
}
