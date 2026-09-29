import { apiClient as client } from "./http";

export type Track = {
  id: number;
  title: string;
  provider: string;
  royaltyPoolPda: string | null;
};
export type RoyaltySplit = { id: number; party: string; shareBps: number };
export type GeoTier = {
  id: number;
  code: string;
  name: string;
  usdcMonthly: number;
  /** u8 priced by subscribe_geographic (1 local .. 5 global). */
  onchainTier?: number | null;
};
export type GeoRegion = {
  id: number;
  code: string;
  name: string;
  /** Zone seed for subscribe_geographic (A-Z 0-9 _). */
  onchainGeoCode?: string | null;
};
export type GeographicSubscription = {
  id: number;
  bandId?: number;
  geoTierId?: number;
  geoRegionId?: number;
  expiresAt?: string;
  months?: number;
  geoCode?: string | null;
  onchainExpiresAt?: string | null;
  geographicSubscriptionPda: string | null;
  intendedInstruction: string;
  payTxSignature?: string | null;
  payerWalletPubkey?: string | null;
  paidAt?: string | null;
  onChainStatus?: string | null;
};
export type TourPlan = { id: number; title: string; routeNote: string };
export type ChannelPiece = { id: number; title: string; artistShareBps: number; eh8sShareBps: number };
export type RoyaltyDeposit = {
  id: number;
  trackId: number;
  royaltyTypeId: number;
  amountUsdc: number;
  intendedInstruction: string;
  royaltyPoolPda?: string | null;
  depositTxSignature?: string | null;
  payerWalletPubkey?: string | null;
  depositedAt?: string | null;
  onChainStatus?: string | null;
};

export async function fetchTracks(): Promise<Track[]> {
  const { data } = await client.get<Track[]>("/api/tracks");
  return data;
}

export async function createTrack(input: {
  title: string;
  provider?: string;
  bandId?: number;
}): Promise<Track> {
  const { data } = await client.post<Track>("/api/tracks", input);
  return data;
}
export async function fetchSplits(trackId: number): Promise<RoyaltySplit[]> {
  const { data } = await client.get<RoyaltySplit[]>(`/api/tracks/${trackId}/splits`);
  return data;
}
export async function fetchGeoTiers(): Promise<GeoTier[]> {
  const { data } = await client.get<GeoTier[]>("/api/geo-tiers");
  return data;
}
export async function fetchGeoRegions(): Promise<GeoRegion[]> {
  const { data } = await client.get<GeoRegion[]>("/api/geo-regions");
  return data;
}
export async function fetchGeoSubscriptions(): Promise<GeographicSubscription[]> {
  const { data } = await client.get<GeographicSubscription[]>("/api/geo-subscriptions");
  return data;
}
export async function fetchTourPlans(): Promise<TourPlan[]> {
  const { data } = await client.get<TourPlan[]>("/api/tour-plans");
  return data;
}
export async function fetchChannelPieces(): Promise<ChannelPiece[]> {
  const { data } = await client.get<ChannelPiece[]>("/api/channel-pieces");
  return data;
}
export async function fetchRoyaltyDeposits(): Promise<RoyaltyDeposit[]> {
  const { data } = await client.get<RoyaltyDeposit[]>("/api/royalty-deposits");
  return data;
}
export async function depositRoyalty(body: {
  trackId: number;
  royaltyTypeId: number;
  amountUsdc: number;
}): Promise<RoyaltyDeposit> {
  const { data } = await client.post<RoyaltyDeposit>("/api/royalty-deposits", body);
  return data;
}
/** Creates an unpaid zone subscription; geoCode defaults to the region's on-chain code. */
export async function subscribeGeo(body: {
  bandId: number;
  geoTierId: number;
  geoRegionId: number;
  geoCode?: string;
  months?: number;
}): Promise<GeographicSubscription> {
  const { data } = await client.post<GeographicSubscription>("/api/geo-subscriptions", body);
  return data;
}
export async function createTourPlan(body: {
  bandId: number;
  geoRegionId: number;
  title: string;
  routeNote?: string;
}): Promise<TourPlan> {
  const { data } = await client.post<TourPlan>("/api/tour-plans", body);
  return data;
}
export async function createChannelPiece(body: {
  channelFormatId: number;
  title: string;
  artistShareBps?: number;
  eh8sShareBps?: number;
}): Promise<ChannelPiece> {
  const { data } = await client.post<ChannelPiece>("/api/channel-pieces", body);
  return data;
}
