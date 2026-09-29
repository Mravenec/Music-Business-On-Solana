import { useCallback, useEffect, useState } from "react";
import { fetchBands, fetchMembers, type Band } from "../services/bandService";
import {
  addOpenDate,
  fetchPin,
  requestSlot,
  type VenuePin,
} from "../services/stageMapService";
import type { Booking } from "../services/venueService";
import { statusText } from "./statusText";

export type VenueSlotState = {
  loading: boolean;
  error: string | null;
  pin: VenuePin | null;
  /** Bands the signed-in musician plays in. */
  myBands: Band[];
  request: (showDate: string, bandId?: number) => Promise<Booking>;
  openDate: (availableDate: string) => Promise<void>;
};

/**
 * One venue's pin data for a month, the musician's bands, Request slot, and open-date publishing.
 */
export function useVenueSlot(
  venueId: number,
  month: string,
  myProfileId: number | null
): VenueSlotState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pin, setPin] = useState<VenuePin | null>(null);
  const [myBands, setMyBands] = useState<Band[]>([]);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(venueId)) return;
    setLoading(true);
    try {
      setPin(await fetchPin(venueId, month));
      if (myProfileId != null) {
        const bands = await fetchBands();
        const rosters = await Promise.all(bands.map((b) => fetchMembers(b.id)));
        setMyBands(
          bands.filter((_, i) => rosters[i].some((m) => m.musicianProfileId === myProfileId))
        );
      } else {
        setMyBands([]);
      }
      setError(null);
    } catch (err) {
      setError(statusText(err, { 404: "That venue was not found." }, "Could not load this venue."));
    } finally {
      setLoading(false);
    }
  }, [venueId, month, myProfileId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    loading,
    error,
    pin,
    myBands,
    request: async (showDate, bandId) => {
      try {
        const row = await requestSlot(venueId, { showDate, bandId });
        await refresh();
        return row;
      } catch (err) {
        throw new Error(
          statusText(
            err,
            {
              400: "Pick an open date and one of your bands.",
              403: "Only musicians in that band can request a slot.",
              409: "That date is no longer open, or your band already requested it.",
            },
            "Could not request the slot."
          )
        );
      }
    },
    openDate: async (availableDate) => {
      try {
        await addOpenDate(venueId, availableDate);
      } catch (err) {
        throw new Error(
          statusText(
            err,
            {
              400: "Pick today or a later date.",
              403: "Only the venue wallet can publish dates.",
              409: "That date is already open.",
            },
            "Could not open the date."
          )
        );
      }
      await refresh();
    },
  };
}
