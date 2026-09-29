import { apiClient as client } from "./http";

export type ClaimRoyaltiesAccount = {
  name: string;
  pubkey: string | null;
  isWritable: boolean;
  isSigner: boolean;
  pdaSeeds?: string[];
  note?: string;
};

export type ClaimRoyaltiesBuild = {
  instruction: string;
  programId: string;
  rpcUrl: string;
  usdcMint: string;
  amountUsdc: number;
  amountAtomic: number;
  discriminatorHex: string;
  dataHex: string;
  accounts: ClaimRoyaltiesAccount[];
  claimId: number;
  concertSettlementId?: number | null;
  ownerWalletPubkey?: string | null;
};

/**
 * Builds the claim_royalties DevNet instruction payload for a pending claim.
 * Backend requires the settlement to already be confirmed on-chain.
 */
export async function buildClaimRoyalties(
  claimId: number,
  body: { walletPubkey: string; vaultUsdcAta: string; musicianUsdcAta: string },
): Promise<ClaimRoyaltiesBuild> {
  const { data } = await client.post<ClaimRoyaltiesBuild>(
    `/api/pending-claims/${claimId}/claim-royalties/build`,
    body,
  );
  return data;
}

/**
 * Confirms a wallet-signed claim_royalties DevNet tx via RPC before marking the claim claimed.
 * Refuses locally when the signature is blank — the backend refuses too.
 */
export async function confirmClaimRoyalties(
  claimId: number,
  body: { walletPubkey: string; txSignature: string; claimPda: string },
) {
  if (!body.txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to mark claimed without a DevNet signature");
  }
  const { data } = await client.post(
    `/api/pending-claims/${claimId}/claim-royalties/confirm`,
    body,
  );
  return data;
}

export type SettleMemberShare = {
  wallet: string;
  bps: number;
  pendingUsdc: number;
};

export type SettleConcertBuild = ClaimRoyaltiesBuild & {
  settlementId?: number;
  concertId?: number;
  bandId: number;
  bandVaultPda?: string | null;
  /** BandVault member wallets in vault order — remaining accounts for settle_concert. */
  memberWallets: string[];
  members: SettleMemberShare[];
  /** The program computes net = gross − expenses, then splits net by protocolFeeBps. */
  grossUsdc?: number;
  expensesUsdc?: number;
  netUsdc?: number;
  protocolFeeBps?: number;
  eh8sFeeUsdc?: number;
  bandPoolUsdc?: number;
};

/**
 * Builds settle_concert for a settlement. Backend answers 409 until the concert's band vault
 * is active on-chain.
 */
export async function buildSettleConcert(
  settlementId: number,
  body: { walletPubkey: string },
): Promise<SettleConcertBuild> {
  const { data } = await client.post<SettleConcertBuild>(
    `/api/concert-settlements/${settlementId}/settle-concert/build`,
    body,
  );
  return data;
}

export async function confirmSettleConcert(
  settlementId: number,
  body: { walletPubkey: string; txSignature: string; concertSettlementPda: string },
) {
  if (!body.txSignature?.trim()) {
    throw new Error("txSignature is required — refusing settle without a DevNet signature");
  }
  const { data } = await client.post(
    `/api/concert-settlements/${settlementId}/settle-concert/confirm`,
    body,
  );
  return data;
}
