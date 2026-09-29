import { useCallback, useEffect, useState } from "react";
import {
  buildSongPool,
  confirmSongPool,
  fetchSongPool,
  type SongPoolStatus,
} from "../services/songRoyaltyService";
import {
  buildDepositRoyalties,
  confirmDepositRoyalties,
} from "../services/royaltyGeoOnchainService";
import { stepMessage, useWalletStep } from "./useWalletStep";

export type SongPoolState = {
  loading: boolean;
  error: string | null;
  pool: SongPoolStatus | null;
  busy: boolean;
  walletPubkey: string | null;
  refresh: () => Promise<void>;
  /** Owner or WAVE agent signs create_royalty_pool with the proposed splits. */
  activate: () => Promise<string>;
  /** Owner or WAVE agent signs deposit_royalties for one recorded deposit; returns the signature. */
  payDeposit: (depositId: number) => Promise<string>;
};

/**
 * One song's royalty pool: proposed or activated splits and on-chain totals.
 */
export function useSongPool(trackId: number): SongPoolState {
  const step = useWalletStep();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pool, setPool] = useState<SongPoolStatus | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setPool(await fetchSongPool(trackId));
      setError(null);
    } catch (err) {
      setError(stepMessage(err, "Could not load the song pool"));
    } finally {
      setLoading(false);
    }
  }, [trackId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const activate = useCallback(async () => {
    const signature = await step.run(
      (walletPubkey) => buildSongPool(trackId, { walletPubkey }),
      (walletPubkey, txSignature) => confirmSongPool(trackId, { walletPubkey, txSignature }),
    );
    await refresh();
    return signature;
  }, [step, trackId, refresh]);

  const payDeposit = useCallback(
    async (depositId: number) => {
      const signature = await step.run(
        (walletPubkey) => buildDepositRoyalties(depositId, { walletPubkey }),
        (walletPubkey, txSignature) =>
          confirmDepositRoyalties(depositId, { walletPubkey, txSignature }),
      );
      await refresh();
      return signature;
    },
    [step, refresh],
  );

  return {
    loading,
    error,
    pool,
    busy: step.busy,
    walletPubkey: step.walletPubkey,
    refresh,
    activate,
    payDeposit,
  };
}
