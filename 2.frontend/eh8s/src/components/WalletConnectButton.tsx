import { useEffect, useRef, type ComponentType } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { WalletMultiButton, useWalletModal } from "@solana/wallet-adapter-react-ui";
import { getAccessToken } from "../services/http";

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
 * - a wallet is already chosen and `eh8s.jwt` is still stored: reconnect once on load.
 *   That brings the address back after F5 without a new signature. Provider
 *   `autoConnect` stays off, so a first visit does not connect by itself.
 * - a wallet is selected but not yet connected, and there is no studio token:
 *   show "Connect <Wallet>" as a real click plus a "Wrong wallet?" link.
 * - connected: hand off to stock WalletMultiButton, whose dropdown (Change wallet /
 *   Disconnect / Copy address) works correctly once actually connected.
 */
export function WalletConnectButton() {
  const { connected, connecting, wallet, connect } = useWallet();
  const { setVisible } = useWalletModal();
  const tried = useRef(false);

  useEffect(() => {
    if (tried.current || connected || connecting || !wallet) return;
    if (!getAccessToken()) return;
    tried.current = true;
    void connect().catch(() => undefined);
  }, [connected, connecting, wallet, connect]);

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
