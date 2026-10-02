import { useEffect, useState } from "react";
import { useConnection } from "@solana/wallet-adapter-react";
import { readWalletContents, type WalletContents } from "../services/walletBalanceService";
import { useChainConfig } from "./useChainConfig";
import { useSession } from "./useSession";

/**
 * SOL and USDC held by the signed-in wallet.
 */
export function useWalletContents() {
  const { connection } = useConnection();
  const { session } = useSession();
  const chain = useChainConfig();
  const owner = session?.account?.walletPubkey ?? null;
  const mint = chain.data?.usdcMint ?? null;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<WalletContents | null>(null);

  useEffect(() => {
    if (!owner || !mint) {
      setData(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    readWalletContents(connection, owner, mint)
      .then((row) => {
        if (cancelled) return;
        setData(row);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setData(null);
        setError("Balance unavailable");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [connection, owner, mint]);

  return { loading, error, data };
}
