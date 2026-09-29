import { useCallback, useEffect, useState } from "react";
import {
  buildCancelBooking,
  buildProposeBooking,
  buildRegisterVenue,
  confirmCancelBooking,
  confirmProposeBooking,
  confirmRegisterVenue,
  fetchVenueConsole,
  type VenueConsole,
} from "../services/venueBookingService";
import { stepMessage, useWalletStep } from "./useWalletStep";

export type VenueEscrowState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  console: VenueConsole | null;
  walletPubkey: string | null;
  /** True when the connected wallet is the venue's listed wallet. */
  isVenueWallet: boolean;
  register: () => Promise<string>;
  propose: (bookingId: number, grossUsdc: string) => Promise<string>;
  cancel: (bookingId: number) => Promise<string>;
};

/**
 * One venue's on-chain listing and its booking escrows, signed by the venue wallet.
 */
export function useVenueEscrow(venueId: number): VenueEscrowState {
  const step = useWalletStep();
  const [data, setData] = useState<VenueConsole | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(venueId)) return;
    setLoading(true);
    try {
      setData(await fetchVenueConsole(venueId));
      setError(null);
    } catch (err) {
      setData(null);
      setError(stepMessage(err, "Could not load the venue"));
    } finally {
      setLoading(false);
    }
  }, [venueId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const after = useCallback(
    async (sig: string) => {
      await refresh();
      return sig;
    },
    [refresh],
  );

  const register = useCallback(
    () =>
      step
        .run(
          (w) => buildRegisterVenue(venueId, { walletPubkey: w }),
          (w, txSignature) => confirmRegisterVenue(venueId, { walletPubkey: w, txSignature }),
        )
        .then(after),
    [step, venueId, after],
  );

  const propose = useCallback(
    (bookingId: number, grossUsdc: string) =>
      step
        .run(
          (w) => buildProposeBooking(bookingId, { walletPubkey: w, grossUsdc }),
          (w, txSignature) =>
            confirmProposeBooking(bookingId, { walletPubkey: w, grossUsdc, txSignature }),
        )
        .then(after),
    [step, after],
  );

  const cancel = useCallback(
    (bookingId: number) =>
      step
        .run(
          (w) => buildCancelBooking(bookingId, { walletPubkey: w }),
          (w, txSignature) => confirmCancelBooking(bookingId, { walletPubkey: w, txSignature }),
        )
        .then(after),
    [step, after],
  );

  const listed = data?.venue.walletPubkey ?? null;
  return {
    loading,
    busy: step.busy,
    error,
    console: data,
    walletPubkey: step.walletPubkey,
    isVenueWallet: Boolean(listed && listed === step.walletPubkey),
    register,
    propose,
    cancel,
  };
}
