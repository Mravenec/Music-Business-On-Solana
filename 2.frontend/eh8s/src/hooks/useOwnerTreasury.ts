import { useCallback, useEffect, useState } from "react";
import { useConnection, useWallet } from "@solana/wallet-adapter-react";
import { PublicKey } from "@solana/web3.js";
import axios from "axios";
import {
  buildWithdrawTreasury,
  confirmWithdrawTreasury,
  fetchOwnerTreasury,
  type OwnerTreasury,
} from "../services/treasuryService";
import { sendWithdrawTreasuryTx } from "../services/withdrawTreasuryTx";

export type OwnerTreasuryState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  treasury: OwnerTreasury | null;
  /** True once a wallet is connected (the treasury is keyed on the owner wallet). */
  walletReady: boolean;
  /** Build → owner wallet signs withdraw_treasury → backend confirms over RPC. */
  withdraw: (amountUsdc: number) => Promise<string>;
};

function apiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) {
      return String((data as { message: string }).message);
    }
    if (status === 401) return "Connect your wallet to sign in first.";
    if (status === 403) return "Only the owner wallet can open the treasury.";
    if (status === 409) return "The treasury does not hold that much USDC.";
    if (status === 400) return "The withdrawal could not be verified on DevNet.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

/**
 * Owner treasury: live on-chain balance of the treasury PDA plus the wallet-signed withdraw.
 */
export function useOwnerTreasury(): OwnerTreasuryState {
  const { connection } = useConnection();
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [treasury, setTreasury] = useState<OwnerTreasury | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setTreasury(null);
      return;
    }
    setLoading(true);
    try {
      setTreasury(await fetchOwnerTreasury(walletPubkey));
      setError(null);
    } catch (err) {
      setTreasury(null);
      setError(apiMessage(err, "Could not load the treasury"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const withdraw = useCallback(
    async (amountUsdc: number): Promise<string> => {
      if (!wallet.publicKey || !wallet.sendTransaction) {
        throw new Error("Connect the owner wallet first.");
      }
      const owner = wallet.publicKey;
      const ownerKey = owner.toBase58();
      setBusy(true);
      try {
        const build = await buildWithdrawTreasury({ walletPubkey: ownerKey, amountUsdc });
        const signature = await sendWithdrawTreasuryTx({
          connection,
          owner,
          programId: new PublicKey(build.programId),
          usdcMint: new PublicKey(build.usdcMint),
          expectedTreasuryUsdc: build.treasuryUsdc,
          dataHex: build.dataHex,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await confirmWithdrawTreasury({ walletPubkey: ownerKey, amountUsdc, txSignature: signature });
        await refresh();
        return signature;
      } catch (err) {
        throw new Error(apiMessage(err, "Wallet transaction failed"));
      } finally {
        setBusy(false);
      }
    },
    [wallet, connection, refresh],
  );

  return { loading, busy, error, treasury, walletReady: Boolean(walletPubkey), withdraw };
}
