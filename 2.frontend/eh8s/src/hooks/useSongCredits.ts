import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { fetchSongCredits, type SongCredit } from "../services/songRoyaltyService";
import { stepMessage } from "./useWalletStep";

export type SongCreditsState = {
  walletPubkey: string | null;
  loading: boolean;
  error: string | null;
  credits: SongCredit[];
  totalUsdc: number;
  refresh: () => Promise<void>;
};

/**
 * Per-song credits (deposits + sync licenses) for the connected musician wallet.
 */
export function useSongCredits(): SongCreditsState {
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [credits, setCredits] = useState<SongCredit[]>([]);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setCredits([]);
      return;
    }
    setLoading(true);
    try {
      setCredits(await fetchSongCredits(walletPubkey));
      setError(null);
    } catch (err) {
      setError(stepMessage(err, "Could not load song credits"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const totalUsdc = credits.reduce((sum, c) => sum + Number(c.creditUsdc), 0);
  return { walletPubkey, loading, error, credits, totalUsdc, refresh };
}
