import { useCallback, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { Connection } from "@solana/web3.js";
import axios from "axios";
import { sendBuiltIx, type BuiltIx } from "../services/sendBuiltIx";

/** Plain-language text for an API or wallet failure. */
export function stepMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) {
      return String((data as { message: string }).message);
    }
    if (status === 401) return "Connect your wallet to sign in first.";
    if (status === 403) return "This wallet does not hold the power for this step.";
    if (status === 409) return "This step is not available in the current state.";
    if (status === 400) return "The transaction could not be verified on DevNet.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

export type WalletStep = {
  walletPubkey: string | null;
  busy: boolean;
  /** Build on the backend → connected wallet signs → RPC confirm → backend verifies and records. */
  run: (
    build: (walletPubkey: string) => Promise<BuiltIx>,
    confirm: (walletPubkey: string, txSignature: string) => Promise<unknown>,
  ) => Promise<string>;
};

/**
 * One wallet-signed program step. The backend never signs; it builds and later verifies.
 */
export function useWalletStep(): WalletStep {
  const wallet = useWallet();
  const [busy, setBusy] = useState(false);

  const run = useCallback<WalletStep["run"]>(
    async (build, confirm) => {
      if (!wallet.publicKey || !wallet.sendTransaction) {
        throw new Error("Connect your wallet first.");
      }
      const payer = wallet.publicKey;
      const walletPubkey = payer.toBase58();
      setBusy(true);
      try {
        const built = await build(walletPubkey);
        const signature = await sendBuiltIx({
          build: built,
          payer,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await new Connection(built.rpcUrl, "confirmed").confirmTransaction(signature, "confirmed");
        await confirm(walletPubkey, signature);
        return signature;
      } catch (err) {
        throw new Error(stepMessage(err, "Wallet transaction failed"));
      } finally {
        setBusy(false);
      }
    },
    [wallet],
  );

  return { walletPubkey: wallet.publicKey?.toBase58() ?? null, busy, run };
}
