import { useCallback, useEffect, useState } from "react";
import {
  buildSyncPay,
  confirmSyncPay,
  createSyncDeal,
  fetchSyncDeals,
  type SyncDeal,
} from "../services/songRoyaltyService";
import { stepMessage, useWalletStep } from "./useWalletStep";

export type SyncDealsState = {
  loading: boolean;
  error: string | null;
  deals: SyncDeal[];
  busy: boolean;
  walletPubkey: string | null;
  refresh: () => Promise<void>;
  create: (input: {
    licenseeName: string;
    useDescription?: string;
    amountUsdc: number;
  }) => Promise<SyncDeal>;
  /** Licensee wallet signs pay_sync_license: 20% EH8S treasury, 80% to the song members. */
  pay: (dealId: number) => Promise<string>;
};

/**
 * Sync license deals for one song.
 */
export function useSyncDeals(trackId: number): SyncDealsState {
  const step = useWalletStep();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deals, setDeals] = useState<SyncDeal[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setDeals(await fetchSyncDeals(trackId));
      setError(null);
    } catch (err) {
      setError(stepMessage(err, "Could not load sync deals"));
    } finally {
      setLoading(false);
    }
  }, [trackId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const create = useCallback<SyncDealsState["create"]>(
    async (input) => {
      try {
        const row = await createSyncDeal({ trackId, ...input });
        await refresh();
        return row;
      } catch (err) {
        throw new Error(stepMessage(err, "Could not propose the deal"));
      }
    },
    [trackId, refresh],
  );

  const pay = useCallback(
    async (dealId: number) => {
      const signature = await step.run(
        (walletPubkey) => buildSyncPay(dealId, { walletPubkey }),
        (walletPubkey, txSignature) => confirmSyncPay(dealId, { walletPubkey, txSignature }),
      );
      await refresh();
      return signature;
    },
    [step, refresh],
  );

  return {
    loading,
    error,
    deals,
    busy: step.busy,
    walletPubkey: step.walletPubkey,
    refresh,
    create,
    pay,
  };
}
