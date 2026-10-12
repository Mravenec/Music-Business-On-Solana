import { useCallback, useEffect, useState } from "react";
import {
  bookShowWithConcert,
  createConcert,
  createVenue,
  fetchBookings,
  fetchClaims,
  fetchConcerts,
  fetchContractTypes,
  fetchVenues,
  type Booking,
  type Concert,
  type ConcertSettlement,
  type PendingClaim,
  type Venue,
} from "../services/venueService";

export type StageMapState = {
  loading: boolean;
  error: string | null;
  venues: Venue[];
  contractTypes: { id: number; code: string; name: string }[];
  bookings: Booking[];
  concerts: Concert[];
  claims: PendingClaim[];
  lastSettlement: ConcertSettlement | null;
  settlementId: number | null;
  setSettlementId: (id: number | null) => void;
  refresh: () => Promise<void>;
  bookShow: (input: {
    venueId: number;
    bandId: number;
    showDate: string;
    pipelineWeek?: number;
  }) => Promise<Concert>;
  scheduleConcert: (input: {
    bookingId: number;
    venueId: number;
    bandId: number;
    sppCycleId?: number;
  }) => Promise<Concert>;
  addVenue: (input: {
    code: string;
    name: string;
    city?: string;
    capacity: number;
    suggestedTicketUsdc: number;
    contractTypeId: number;
  }) => Promise<Venue>;
};

/**
 * Live stage-map data plus book / claim mutations.
 * Show settle is on-chain booking escrow only (/agents/vault → settle/confirm).
 */
export function useStageMap(): StageMapState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [contractTypes, setContractTypes] = useState<{ id: number; code: string; name: string }[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [concerts, setConcerts] = useState<Concert[]>([]);
  const [claims, setClaims] = useState<PendingClaim[]>([]);
  const [lastSettlement] = useState<ConcertSettlement | null>(null);
  const [settlementId, setSettlementId] = useState<number | null>(1);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [v, b, c] = await Promise.all([
        fetchVenues(),
        fetchBookings(),
        fetchConcerts(),
      ]);
      let claimRows: PendingClaim[] = [];
      if (settlementId) {
        try {
          claimRows = await fetchClaims(settlementId);
        } catch {
          claimRows = [];
        }
      }
      setVenues(v);
      setBookings(b);
      setConcerts(c);
      setClaims(claimRows);
      setError(null);
    } catch (err: unknown) {
      const message =
        err && typeof err === "object" && "message" in err
          ? String((err as { message: string }).message)
          : "Network Error";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [settlementId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    void fetchContractTypes()
      .then(setContractTypes)
      .catch(() => setContractTypes([]));
  }, []);

  return {
    loading,
    error,
    venues,
    contractTypes,
    bookings,
    concerts,
    claims,
    lastSettlement,
    settlementId,
    setSettlementId,
    refresh,
    bookShow: async (input) => {
      const row = await bookShowWithConcert(input);
      await refresh();
      return row;
    },
    scheduleConcert: async (input) => {
      const row = await createConcert(input);
      await refresh();
      return row;
    },
    addVenue: async (input) => {
      const row = await createVenue({
        code: input.code,
        name: input.name,
        city: input.city || "CDMX",
        countryCode: "MEX",
        latitude: 19.4326,
        longitude: -99.1332,
        pinStatus: "listed",
        capacity: input.capacity,
        suggestedTicketUsdc: input.suggestedTicketUsdc,
        contractTypeId: input.contractTypeId,
      });
      await refresh();
      return row;
    },
  };
}
