import { apiClient as client } from "./http";

/** JOOQ OwnerDigest POJO keys. */
export type OwnerDigest = {
  id: number;
  ownerWalletPubkey: string;
  model: string;
  metricsJson: string;
  body: string;
  inputTokens: number | null;
  outputTokens: number | null;
  slackStatus: string | null;
  createdAt: string;
};

export type OwnerDigestLatest = {
  aiConfigured: boolean;
  model: string;
  latest: OwnerDigest | null;
};

/** Latest stored digest and whether the backend has an Anthropic key (owner wallet only). */
export async function fetchLatestDigest(walletPubkey: string): Promise<OwnerDigestLatest> {
  const { data } = await client.get<OwnerDigestLatest>("/api/owner/digest/latest", {
    params: { walletPubkey },
  });
  return data;
}

/** Asks Claude for today's digest from live metrics; 503 when no key is configured. */
export async function runDigest(walletPubkey: string): Promise<OwnerDigest> {
  const { data } = await client.post<OwnerDigest>("/api/owner/digest/run", { walletPubkey });
  return data;
}
