import { apiClient as client } from "./http";

export type OnchainBuild = {
  instruction: string;
  programId: string;
  rpcUrl: string;
  usdcMint: string;
  amountUsdc?: number;
  geoCode?: string;
  tier?: number;
  months?: number;
  geographicSubscriptionPda?: string;
  dataHex?: string;
  ownerWalletPubkey?: string;
  accounts?: Array<{ name: string; pubkey: string | null; isWritable?: boolean; isSigner?: boolean }>;
};

/** Builds per-song deposit_royalties (owner or WAVE agent; the song pool must be active). */
export async function buildDepositRoyalties(
  depositId: number,
  body: { walletPubkey: string }
): Promise<OnchainBuild> {
  const { data } = await client.post<OnchainBuild>(
    `/api/royalty-deposits/${depositId}/deposit-royalties/build`,
    body
  );
  return data;
}

export async function confirmDepositRoyalties(
  depositId: number,
  body: { walletPubkey: string; txSignature: string }
) {
  const { data } = await client.post(
    `/api/royalty-deposits/${depositId}/deposit-royalties/confirm`,
    body
  );
  return data;
}

/** On-chain GeographicSubscription read (GET .../onchain or /geo-zones/{code}/status). */
export type ZoneOnchainStatus = {
  geoCode: string;
  geographicSubscriptionPda: string;
  exists: boolean;
  expiresAt: string | null;
  active: boolean;
};

/** Builds subscribe_geographic(geo_code, tier, months) metadata; the amount is never sent. */
export async function buildSubscribeGeographic(
  subscriptionId: number,
  body: { walletPubkey: string; payerUsdcAta?: string }
): Promise<OnchainBuild> {
  const { data } = await client.post<OnchainBuild>(
    `/api/geo-subscriptions/${subscriptionId}/subscribe-geographic/build`,
    body
  );
  return data;
}

export async function confirmSubscribeGeographic(
  subscriptionId: number,
  body: {
    walletPubkey: string;
    txSignature: string;
    geographicSubscriptionPda: string;
  }
) {
  const { data } = await client.post(
    `/api/geo-subscriptions/${subscriptionId}/subscribe-geographic/confirm`,
    body
  );
  return data;
}

/** Reads a confirmed zone subscription's expiry live from DevNet. */
export async function fetchGeoOnchainStatus(subscriptionId: number): Promise<ZoneOnchainStatus> {
  const { data } = await client.get<ZoneOnchainStatus>(
    `/api/geo-subscriptions/${subscriptionId}/onchain`
  );
  return data;
}

/**
 * Confirms a wallet-signed claim_royalties DevNet tx against a pending_claim.
 * Backend must refuse blank signatures; FE never calls this without a real sig.
 * Uses the same RPC-verified endpoint as the Stage settle/claim flow —
 * the legacy /devnet-claim marker route now always refuses with a redirect message.
 */
export async function confirmClaimRoyalties(
  claimId: number,
  body: { walletPubkey: string; txSignature: string; claimPda: string }
) {
  if (!body.txSignature?.trim()) {
    throw new Error("txSignature is required — refusing claim without a DevNet signature");
  }
  const { data } = await client.post(
    `/api/pending-claims/${claimId}/claim-royalties/confirm`,
    body
  );
  return data;
}
