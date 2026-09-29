import { apiClient as client } from "./http";
import type { Booking } from "./venueService";

export type PinColor = "purple" | "yellow" | "green" | "red";

/** venue POJO fields the popup shows. */
export type PinVenue = {
  id: number;
  name: string;
  city: string;
  capacity: number;
  latitude: number;
  longitude: number;
  eh8sRating: number;
  preferredGenres: string | null;
  isPartner: number;
  listingStatus: string | null;
  walletPubkey: string | null;
};

/** GET /api/stage-map/pins item. */
export type VenuePin = {
  venueId: number;
  venue: PinVenue;
  pin: PinColor;
  pinLabel: string;
  contractType: string | null;
  availableDays: string[];
  nextShow: Booking | null;
  month: string;
};

/** venue_availability row (JOOQ POJO JSON). */
export type VenueAvailability = { id: number; venueId: number; availableDate: string };

export async function fetchPins(month: string): Promise<VenuePin[]> {
  const { data } = await client.get<VenuePin[]>("/api/stage-map/pins", { params: { month } });
  return data;
}

export async function fetchPin(venueId: number, month: string): Promise<VenuePin> {
  const { data } = await client.get<VenuePin>(`/api/stage-map/venues/${venueId}`, {
    params: { month },
  });
  return data;
}

export async function addOpenDate(venueId: number, availableDate: string): Promise<VenueAvailability> {
  const { data } = await client.post<VenueAvailability>(
    `/api/stage-map/venues/${venueId}/availability`,
    { availableDate }
  );
  return data;
}

/** Creates a booking with status "requested" for the signed-in musician's band. */
export async function requestSlot(
  venueId: number,
  input: { showDate: string; bandId?: number }
): Promise<Booking> {
  const { data } = await client.post<Booking>(`/api/stage-map/venues/${venueId}/slot-requests`, input);
  return data;
}
