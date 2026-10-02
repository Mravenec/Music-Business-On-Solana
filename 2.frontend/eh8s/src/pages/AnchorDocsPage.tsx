import { useState } from "react";
import { useAnchorMetadata } from "../hooks/useAnchorMetadata";

const DEPLOY_STEPS = `cd 4.anchor/eh8s-devnet
solana config set --url https://api.devnet.solana.com
solana airdrop 2
anchor build --ignore-keys
anchor deploy --provider.cluster devnet
# then UPDATE chain_config SET program_deployed_at = UTC_TIMESTAMP()`;

/**
 * Live DevNet Anchor program status — instructions and deploy smoke notes.
 * Mainnet is shown so the choice is visible. It does not move wallet flows.
 */
export function AnchorDocsPage() {
  const { loading, error, data } = useAnchorMetadata();
  const [networkView, setNetworkView] = useState<"devnet" | "mainnet">("devnet");
  const instructions = Array.isArray(data?.instructions)
    ? (data.instructions as string[])
    : [];
  const onDevnet = networkView === "devnet";

  return (
    <section className="eh8s-page">
      <header className="eh8s-page-hero">
        <p className="eh8s-kicker">Solana</p>
        <h1>Programs</h1>
        <p className="eh8s-lead">
          The EH8S program that wallet flows call. DevNet is live. Mainnet is not open.
        </p>
      </header>

      <label className="eh8s-field eh8s-network-field">
        Network
        <select
          value={networkView}
          onChange={(e) => setNetworkView(e.target.value === "mainnet" ? "mainnet" : "devnet")}
          aria-label="Network"
        >
          <option value="devnet">DevNet — live</option>
          <option value="mainnet">Mainnet — not open</option>
        </select>
      </label>

      {!onDevnet ? (
        <div className="eh8s-banner bad">
          Mainnet is not open. The facts below stay on DevNet. Nothing here moves funds to Mainnet.
        </div>
      ) : null}

      {loading ? <p className="eh8s-muted-line">Loading program metadata…</p> : null}
      {error ? (
        <div className="eh8s-banner bad">Anchor metadata unavailable: {error}</div>
      ) : null}

      {data ? (
        <>
          <div className="eh8s-stats">
            <article className="eh8s-stat">
              <span className="eh8s-stat-label">Network</span>
              <strong>{onDevnet ? "DevNet" : "Not open"}</strong>
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
              <strong>{onDevnet ? "Closed" : "Not open"}</strong>
            </article>
          </div>

          <div className="eh8s-section-head">
            <h2>Program identity</h2>
            <p>These values must match the DevNet deploy.</p>
          </div>
          <div className="eh8s-fact-grid">
            <article className="eh8s-fact">
              <span>Program id</span>
              <code>{data.programIdDevnet}</code>
            </article>
            <article className="eh8s-fact">
              <span>RPC</span>
              <code>{data.rpcUrl}</code>
            </article>
            <article className="eh8s-fact">
              <span>USDC mint</span>
              <code>{data.usdcMint}</code>
            </article>
            <article className="eh8s-fact">
              <span>Source</span>
              <code>{data.anchorScaffoldPath ?? "4.anchor/eh8s-devnet"}</code>
            </article>
            <article className="eh8s-fact">
              <span>Owner wallet</span>
              <code>{String(data.ownerWalletPubkey ?? "—")}</code>
            </article>
          </div>

          <div className="eh8s-section-head">
            <h2>Instructions</h2>
            <p>Each one needs a real wallet signature.</p>
          </div>
          <ol className="eh8s-ix-list">
            {instructions.map((ix, index) => (
              <li key={ix}>
                <span>{index + 1}</span>
                <code>{ix}</code>
              </li>
            ))}
          </ol>

          {data.note ? <div className="eh8s-banner ok">{data.note}</div> : null}
        </>
      ) : null}

      <div className="eh8s-section-head">
        <h2>Deploy on this machine</h2>
        <p>Solana CLI and a funded DevNet wallet.</p>
      </div>
      <div className="eh8s-panel eh8s-code-panel">
        <span className="eh8s-code-label">DevNet</span>
        <pre>{DEPLOY_STEPS}</pre>
      </div>
    </section>
  );
}
