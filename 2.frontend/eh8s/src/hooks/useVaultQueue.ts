import { useCallback, useEffect, useState } from "react";
import {
  buildSettleBooking,
  confirmSettleBooking,
  fetchVaultQueue,
  type SettleBookingBuild,
  type VaultQueue,
} from "../services/venueBookingService";
import { stepMessage, useWalletStep } from "./useWalletStep";

export type VaultQueueState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  queue: VaultQueue | null;
  walletReady: boolean;
  /** Builds settle_booking without signing to show the fee / pool / member split. */
  previewSettle: (bookingId: number) => Promise<SettleBookingBuild>;
  settle: (bookingId: number) => Promise<string>;
};

/**
 * Owner / VAULT agent queue: settle confirmed booking escrows on DevNet.
 */
export function useVaultQueue(): VaultQueueState {
  const step = useWalletStep();
  const walletPubkey = step.walletPubkey;
  const [queue, setQueue] = useState<VaultQueue | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setQueue(null);
      return;
    }
    setLoading(true);
    try {
      setQueue(await fetchVaultQueue(walletPubkey));
      setError(null);
    } catch (err) {
      setQueue(null);
      setError(stepMessage(err, "Could not load the settlement queue"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const previewSettle = useCallback(
    async (bookingId: number) => {
      if (!walletPubkey) throw new Error("Connect the owner or VAULT agent wallet.");
      return buildSettleBooking(bookingId, { walletPubkey });
    },
    [walletPubkey],
  );

  const settle = useCallback(
    async (bookingId: number) => {
      const sig = await step.run(
        (w) => buildSettleBooking(bookingId, { walletPubkey: w }),
        (w, txSignature) => confirmSettleBooking(bookingId, { walletPubkey: w, txSignature }),
      );
      await refresh();
      return sig;
    },
    [step, refresh],
  );

  return {
    loading,
    busy: step.busy,
    error,
    queue,
    walletReady: Boolean(walletPubkey),
    previewSettle,
    settle,
  };
}
