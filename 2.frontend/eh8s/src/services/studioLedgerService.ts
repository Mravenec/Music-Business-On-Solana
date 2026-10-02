import { apiClient as client } from "./http";

export type StudioClaim = {
  id: number;
  amountUsdc: number;
};

export type VenueExpense = {
  id: number;
  expensesUsdc: number;
};

export type InstructorShare = {
  id: number;
  instructorUsdc: number;
};

export type PartnerAllocation = {
  id: number;
  studioPartnerId: number;
  sourceCode: string;
  amountUsdc: number;
};

export type StudioPartner = {
  id: number;
  walletPubkey: string;
  displayName: string;
  shareBps: number;
  active: number;
};

export type ShowFee = { id: number; eh8sFeeUsdc: number };
export type AcademyFee = { id: number; treasuryUsdc: number };
export type SyncFee = { id: number; eh8sUsdc: number };

async function monthGet<T>(path: string, year: number, month: number): Promise<T[]> {
  const { data } = await client.get<T[]>(path, { params: { year, month } });
  return data;
}

/** Confirmed solo-show claims for the signed-in non-owner wallet. */
export function fetchSoloClaims(year: number, month: number) {
  return monthGet<StudioClaim>("/api/studio-ledger/solo-claims", year, month);
}

/** Confirmed band-show claims for the signed-in non-owner wallet. */
export function fetchBandClaims(year: number, month: number) {
  return monthGet<StudioClaim>("/api/studio-ledger/band-claims", year, month);
}

/** Confirmed venue expense returns for the signed-in non-owner wallet. */
export function fetchVenueExpenses(year: number, month: number) {
  return monthGet<VenueExpense>("/api/studio-ledger/venue-expenses", year, month);
}

/** Confirmed tutor shares for the signed-in non-owner wallet. */
export function fetchInstructorShares(year: number, month: number) {
  return monthGet<InstructorShare>("/api/studio-ledger/instructor-shares", year, month);
}

/** This wallet's partner allocation. Empty when the wallet is not a partner. */
export function fetchMyAllocation(year: number, month: number) {
  return monthGet<PartnerAllocation>("/api/studio-ledger/my-allocation", year, month);
}

/** Active partners. Owner only. */
export async function fetchPartners(): Promise<StudioPartner[]> {
  const { data } = await client.get<StudioPartner[]>("/api/studio-ledger/partners");
  return data;
}

/** Adds or updates a partner wallet and share. Owner only. */
export async function savePartner(draft: {
  walletPubkey: string;
  displayName: string;
  shareBps: number;
}): Promise<StudioPartner> {
  const { data } = await client.post<StudioPartner>("/api/studio-ledger/partners", draft);
  return data;
}

/** Removes a partner from the active split. Owner only. */
export async function deactivatePartner(partnerId: number): Promise<StudioPartner> {
  const { data } = await client.delete<StudioPartner>(`/api/studio-ledger/partners/${partnerId}`);
  return data;
}

/** Confirmed show fees for the month. Owner only. */
export function fetchShowFees(year: number, month: number) {
  return monthGet<ShowFee>("/api/studio-ledger/show-fees", year, month);
}

/** Confirmed academy treasury rows for the month. Owner only. */
export function fetchAcademyFees(year: number, month: number) {
  return monthGet<AcademyFee>("/api/studio-ledger/academy-fees", year, month);
}

/** Confirmed sync studio cuts for the month. Owner only. */
export function fetchSyncFees(year: number, month: number) {
  return monthGet<SyncFee>("/api/studio-ledger/sync-fees", year, month);
}

/** Every partner's allocation for the month. Owner only. */
export function fetchAllocations(year: number, month: number) {
  return monthGet<PartnerAllocation>("/api/studio-ledger/allocations", year, month);
}
