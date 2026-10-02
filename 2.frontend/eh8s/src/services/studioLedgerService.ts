import { apiClient as client } from "./http";

export type StudioClaim = {
  id: number;
  amountUsdc: number;
  claimedAt?: string | null;
  onChainStatus?: string | null;
};

export type InstructorShare = {
  id: number;
  instructorUsdc: number;
  paidAt?: string | null;
  onChainStatus?: string | null;
};

/**
 * Confirmed royalty claims the studio paid to the signed-in wallet in one month.
 */
export async function fetchStudioClaims(year: number, month: number): Promise<StudioClaim[]> {
  const { data } = await client.get<StudioClaim[]>("/api/studio-ledger/claims", {
    params: { year, month },
  });
  return data;
}

/**
 * Confirmed instructor shares the studio paid to the signed-in wallet in one month.
 */
export async function fetchInstructorShares(
  year: number,
  month: number
): Promise<InstructorShare[]> {
  const { data } = await client.get<InstructorShare[]>("/api/studio-ledger/instructor-shares", {
    params: { year, month },
  });
  return data;
}
