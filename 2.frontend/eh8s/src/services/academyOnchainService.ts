import { apiClient as client } from "./http";
import type { AcademySubscription } from "./academyService";

export type SubscribeAcademyBuild = {
  instruction: string;
  programId: string;
  rpcUrl: string;
  usdcMint: string;
  /** On-chain plan code (1 basic, 2 band, 3 pro). */
  planType: number;
  months: number;
  /** Plan price x months, computed by Spring from academy_plan. */
  amountUsdc: number;
  academySubscriptionPda: string;
  amountAtomic: number;
  treasuryShareAtomic: number;
  instructorShareAtomic: number;
  treasuryShareBps: number;
  instructorShareBps: number;
  discriminatorHex: string;
  dataHex: string;
  subscriptionId: number;
  ownerWalletPubkey?: string;
  accounts: Array<{
    name: string;
    pubkey: string | null;
    isWritable: boolean;
    isSigner: boolean;
    pdaSeeds?: string[];
    note?: string;
  }>;
};

/** On-chain AcademySubscription read (GET .../onchain). */
export type SubscriptionOnchainStatus = {
  exists: boolean;
  expiresAt: string | null;
  active: boolean;
};

/** Builds subscribe_academy(plan_type, months) metadata from Spring; the amount is never sent. */
export async function buildSubscribeAcademy(
  subscriptionId: number,
  body: {
    walletPubkey: string;
    payerUsdcAta?: string;
    instructorUsdcAta?: string;
  },
): Promise<SubscribeAcademyBuild> {
  const { data } = await client.post<SubscribeAcademyBuild>(
    `/api/academy-subscriptions/${subscriptionId}/subscribe-academy/build`,
    body,
  );
  return data;
}

/** Reads the payer's AcademySubscription PDA expiry live from DevNet. */
export async function fetchAcademyOnchainStatus(
  subscriptionId: number,
): Promise<SubscriptionOnchainStatus> {
  const { data } = await client.get<SubscriptionOnchainStatus>(
    `/api/academy-subscriptions/${subscriptionId}/onchain`,
  );
  return data;
}

/** Confirms a DevNet signature and marks the subscription paid. */
export async function confirmSubscribeAcademy(
  subscriptionId: number,
  body: {
    walletPubkey: string;
    txSignature: string;
    academySubscriptionPda: string;
  },
): Promise<AcademySubscription> {
  const { data } = await client.post<AcademySubscription>(
    `/api/academy-subscriptions/${subscriptionId}/subscribe-academy/confirm`,
    body,
  );
  return data;
}
