import { apiClient as client } from "./http";
import type { Band } from "./bandService";

/** tour_plan row (JOOQ POJO JSON). */
export type TourPlan = {
  id: number;
  bandId: number;
  geoRegionId: number | null;
  title: string;
  routeNote: string | null;
  windowStart: string | null;
  windowEnd: string | null;
  totalKm: number | null;
  projectedIncomeUsdc: number | null;
  generatedBy: string;
  createdAt: string | null;
};

/** tour_plan_stop row (JOOQ POJO JSON). */
export type TourPlanStop = {
  id: number;
  tourPlanId: number;
  stopOrder: number;
  venueId: number;
  showDate: string | null;
  legKm: number | null;
  projectedIncomeUsdc: number | null;
};

export type TourStopRow = {
  stop: TourPlanStop;
  venue: {
    id: number;
    name: string;
    city: string | null;
    latitude: number | null;
    longitude: number | null;
    capacity: number | null;
  } | null;
};

export type TourPlanDetail = { plan: TourPlan; stops: TourStopRow[] };

export type TourRoutes = { band: Band; activeZones: string[]; plans: TourPlanDetail[] };

export type TourRequest = { windowStart: string; windowEnd: string; maxStops: number };

export async function fetchTourRoutes(bandId: number): Promise<TourRoutes> {
  const { data } = await client.get<TourRoutes>(`/api/bands/${bandId}/tour-routes`);
  return data;
}

export async function generateTourRoute(bandId: number, input: TourRequest): Promise<TourPlanDetail> {
  const { data } = await client.post<TourPlanDetail>(`/api/bands/${bandId}/tour-routes`, input);
  return data;
}

export async function fetchTourPlan(planId: number): Promise<TourPlanDetail> {
  const { data } = await client.get<TourPlanDetail>(`/api/tour-routes/${planId}`);
  return data;
}
