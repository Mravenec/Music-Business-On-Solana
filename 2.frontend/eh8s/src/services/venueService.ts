import { apiClient as client } from "./http";

export type Venue = {
  id: number;
  code?: string;
  name: string;
  city: string;
  countryCode?: string;
  capacity: number;
  suggestedTicketUsdc?: number;
  contractTypeId?: number;
  pinStatus: string;
  eh8sRating: number;
  latitude: number;
  longitude: number;
  venueListingPda: string | null;
};

export type Booking = {
  id: number;
  venueId: number;
  bandId: number;
  showDate: string;
  pipelineWeek: number;
  status: string;
};

export type Concert = {
  id: number;
  bookingId: number;
  venueId: number;
  bandId: number;
  sppCycleId?: number | null;
  status: string;
  concertSettlementPda: string | null;
};

export type ConcertSettlement = {
  id: number;
  concertId: number;
  grossUsdc: number;
  expensesUsdc: number;
  netUsdc: number;
  eh8sFeeUsdc: number;
  bandPoolUsdc: number;
  status: string;
};

export type PendingClaim = {
  id: number;
  concertSettlementId?: number;
  musicianProfileId: number;
  shareBps: number;
  amountUsdc: number;
  status: string;
};

export async function fetchContractTypes(): Promise<{ id: number; code: string; name: string }[]> {
  const { data } = await client.get<{ id: number; code: string; name: string }[]>("/api/contract-types");
  return data;
}

export async function fetchVenues(): Promise<Venue[]> {
  const { data } = await client.get<Venue[]>("/api/venues");
  return data;
}

export async function createVenue(input: Partial<Venue> & { code: string; name: string }): Promise<Venue> {
  const { data } = await client.post<Venue>("/api/venues", input);
  return data;
}

export async function fetchBookings(): Promise<Booking[]> {
  const { data } = await client.get<Booking[]>("/api/bookings");
  return data;
}

export async function createBooking(input: {
  venueId: number;
  bandId: number;
  showDate: string;
  pipelineWeek?: number;
  status?: string;
}): Promise<Booking> {
  const { data } = await client.post<Booking>("/api/bookings", input);
  return data;
}

export async function fetchConcerts(): Promise<Concert[]> {
  const { data } = await client.get<Concert[]>("/api/concerts");
  return data;
}

export async function createConcert(input: {
  bookingId: number;
  venueId: number;
  bandId: number;
  sppCycleId?: number;
  status?: string;
}): Promise<Concert> {
  const { data } = await client.post<Concert>("/api/concerts", input);
  return data;
}

export async function settleConcert(
  concertId: number,
  grossUsdc: number,
): Promise<ConcertSettlement> {
  const { data } = await client.post<ConcertSettlement>(`/api/concerts/${concertId}/settle`, {
    grossUsdc,
  });
  return data;
}

export async function fetchClaims(settlementId: number): Promise<PendingClaim[]> {
  const { data } = await client.get<PendingClaim[]>(`/api/settlements/${settlementId}/claims`);
  return data;
}
