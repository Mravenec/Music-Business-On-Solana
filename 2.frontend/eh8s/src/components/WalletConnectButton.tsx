import type { ComponentType } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { WalletMultiButton, useWalletModal } from "@solana/wallet-adapter-react-ui";

const MultiButton = WalletMultiButton as ComponentType;

/**
 * Wallet connect control with a real "wrong wallet" escape hatch.
 *
 * `autoConnect` stays off on the provider: Phantom/Solflare's `autoConnect()` in this
 * SDK version is just `connect()` with no silent/onlyIfTrusted path, so triggering it
 * from a React effect (no user gesture) risks a popup the extension silently blocks or
 * never resolves — leaving the button stuck on the wrong wallet with no way back.
 *
 * Instead:
 * - no wallet selected: "Select Wallet" opens the picker (stock behavior).
 * - a wallet is selected but not yet connected (picked by mistake, or restored from
 *   localStorage on refresh): show "Connect <Wallet>" as a real click (resolves near
 *   instantly if this wallet already trusts the site) plus a "Wrong wallet?" link that
 *   reopens the picker without attempting to connect.
 * - connected: hand off to stock WalletMultiButton, whose dropdown (Change wallet /
 *   Disconnect / Copy address) works correctly once actually connected.
 */
export function WalletConnectButton() {
  const { connected, connecting, wallet, connect } = useWallet();
  const { setVisible } = useWalletModal();

  if (connected) {
    return <MultiButton />;
  }

  if (wallet) {
    return (
      <div className="eh8s-wallet-pick">
        <button
          type="button"
          className="wallet-adapter-button wallet-adapter-button-trigger"
          onClick={() => void connect().catch(() => undefined)}
          disabled={connecting}
        >
          {connecting ? "Connecting…" : `Connect ${wallet.adapter.name}`}
        </button>
        <button
          type="button"
          className="eh8s-wallet-pick-other"
          onClick={() => setVisible(true)}
        >
          Wrong wallet? Choose another
        </button>
      </div>
    );
  }

  return (
    <button
      type="button"
      className="wallet-adapter-button wallet-adapter-button-trigger"
      onClick={() => setVisible(true)}
    >
      Select Wallet
    </button>
  );
}
