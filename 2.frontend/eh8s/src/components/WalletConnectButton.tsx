import { useEffect, useRef, type ComponentType } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { WalletMultiButton, useWalletModal } from "@solana/wallet-adapter-react-ui";
import { formatWalletLabel, getAccessToken, getSignedInWallet } from "../services/http";
import { useSession } from "../hooks/useSession";

const MultiButton = WalletMultiButton as ComponentType;

/**
 * Header wallet control.
 *
 * Provider `autoConnect` stays off. Phantom and Solflare treat that flag as a full
 * `connect()`, and a page-load popup does not restore the address.
 *
 * The label follows the studio session, not the adapter:
 * - signed in with a wallet: the same truncated address after refresh (`7QVK..DUuC`).
 *   No "Connect Solflare", no "Wrong wallet?", no new signature.
 * - session still loading and the address is not stored yet: a quiet placeholder.
 * - no session: "Select Wallet", even if the adapter remembered a wallet name.
 *
 * A background `connect()` may still attach a trusted extension for payments.
 * That attempt does not own the label.
 */
export function WalletConnectButton() {
  const { connected, connecting, wallet, connect } = useWallet();
  const { setVisible } = useWalletModal();
  const { session, loading } = useSession();
  const tried = useRef(false);
  const signedIn = Boolean(getAccessToken());
  const signedInPubkey = session?.account?.walletPubkey || getSignedInWallet() || null;

  useEffect(() => {
    if (tried.current || connected || connecting || !wallet) return;
    if (!signedIn) return;
    tried.current = true;
    void connect().catch(() => undefined);
  }, [connected, connecting, wallet, connect, signedIn]);

  if (connected) {
    return <MultiButton />;
  }

  if (signedIn && signedInPubkey) {
    return (
      <button
        type="button"
        className="wallet-adapter-button wallet-adapter-button-trigger"
        onClick={() => {
          if (wallet) void connect().catch(() => undefined);
          else setVisible(true);
        }}
      >
        {formatWalletLabel(signedInPubkey)}
      </button>
    );
  }

  if (signedIn && loading) {
    return (
      <button type="button" className="wallet-adapter-button wallet-adapter-button-trigger" disabled>
        …
      </button>
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
