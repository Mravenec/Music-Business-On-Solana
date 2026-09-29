import { useCallback, useEffect, useState } from "react";
import {
  buildApproveVenue,
  buildConfirmContract,
  confirmApproveVenue,
  confirmConfirmContract,
  fetchStageQueue,
  type ConfirmContractBuild,
  type StageQueue,
} from "../services/venueBookingService";
import { stepMessage, useWalletStep } from "./useWalletStep";

export type StageQueueState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  queue: StageQueue | null;
  walletReady: boolean;
  approve: (venueId: number) => Promise<string>;
  /** Builds confirm_booking without signing so the contract text can be read first. */
  previewContract: (bookingId: number) => Promise<ConfirmContractBuild>;
  confirmContract: (bookingId: number) => Promise<string>;
};

/**
 * Owner / STAGE agent queue: approve pending venue listings, confirm proposed bookings.
 */
export function useStageQueue(): StageQueueState {
  const step = useWalletStep();
  const walletPubkey = step.walletPubkey;
  const [queue, setQueue] = useState<StageQueue | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setQueue(null);
      return;
    }
    setLoading(true);
    try {
      setQueue(await fetchStageQueue(walletPubkey));
      setError(null);
    } catch (err) {
      setQueue(null);
      setError(stepMessage(err, "Could not load the stage queue"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const approve = useCallback(
    async (venueId: number) => {
      const sig = await step.run(
        (w) => buildApproveVenue(venueId, { walletPubkey: w }),
        (w, txSignature) => confirmApproveVenue(venueId, { walletPubkey: w, txSignature }),
      );
      await refresh();
      return sig;
    },
    [step, refresh],
  );

  const previewContract = useCallback(
    async (bookingId: number) => {
      if (!walletPubkey) throw new Error("Connect the owner or STAGE agent wallet.");
      return buildConfirmContract(bookingId, { walletPubkey });
    },
    [walletPubkey],
  );

  const confirmContract = useCallback(
    async (bookingId: number) => {
      const sig = await step.run(
        (w) => buildConfirmContract(bookingId, { walletPubkey: w }),
        (w, txSignature) => confirmConfirmContract(bookingId, { walletPubkey: w, txSignature }),
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
    approve,
    previewContract,
    confirmContract,
  };
}
