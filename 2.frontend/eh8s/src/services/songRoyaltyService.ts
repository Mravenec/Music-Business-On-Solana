import { apiClient as client } from "./http";
import type { BuiltIx } from "./sendBuiltIx";

/** One member of a song pool (JSON keys match track.pool_splits_json). */
export type SongPoolSplit = { musicianProfileId: number; wallet: string; bps: number };

/** GET /api/tracks/{id}/royalty-pool. */
export type SongPoolStatus = {
  trackId: number;
  title: string;
  royaltyPoolPda: string;
  active: boolean;
  poolActivatedAt: string | null;
  poolTxSignature: string | null;
  splits: SongPoolSplit[] | null;
  proposedSplits?: SongPoolSplit[];
  proposedError?: string;
  onchainExists?: boolean;
  onchainTotalUsdc?: number;
  onchainSyncTotalUsdc?: number;
  onchainError?: string;
};

export type SongIxBuild = BuiltIx & {
  instruction: string;
  royaltyPoolPda: string;
  members?: SongPoolSplit[];
  amountUsdc?: number;
  artistUsdc?: number;
  eh8sUsdc?: number;
};

/** JOOQ SyncLicenseDeal POJO JSON. */
export type SyncDeal = {
  id: number;
  trackId: number;
  licenseeName: string;
  useDescription: string | null;
  amountUsdc: number;
  artistUsdc: number;
  eh8sUsdc: number;
  status: "proposed" | "paid";
  licenseeWalletPubkey: string | null;
  syncLicensePda: string | null;
  payTxSignature: string | null;
  paidAt: string | null;
  createdAt: string | null;
};

/** One per-song credit row for a musician wallet. */
export type SongCredit = {
  trackId: number;
  trackTitle: string;
  source: "deposit" | "sync";
  sourceId: number;
  grossUsdc: number;
  shareBps: number;
  creditUsdc: number;
  txSignature: string | null;
  at: string | null;
};

export async function fetchSongPool(trackId: number): Promise<SongPoolStatus> {
  const { data } = await client.get<SongPoolStatus>(`/api/tracks/${trackId}/royalty-pool`);
  return data;
}

export async function buildSongPool(
  trackId: number,
  body: { walletPubkey: string },
): Promise<SongIxBuild> {
  const { data } = await client.post<SongIxBuild>(
    `/api/tracks/${trackId}/royalty-pool/build`,
    body,
  );
  return data;
}

export async function confirmSongPool(
  trackId: number,
  body: { walletPubkey: string; txSignature: string },
) {
  const { data } = await client.post(`/api/tracks/${trackId}/royalty-pool/confirm`, body);
  return data;
}

export async function fetchSyncDeals(trackId: number): Promise<SyncDeal[]> {
  const { data } = await client.get<SyncDeal[]>("/api/sync-deals", { params: { trackId } });
  return data;
}

export async function createSyncDeal(body: {
  trackId: number;
  licenseeName: string;
  useDescription?: string;
  amountUsdc: number;
}): Promise<SyncDeal> {
  const { data } = await client.post<SyncDeal>("/api/sync-deals", body);
  return data;
}

export async function buildSyncPay(
  dealId: number,
  body: { walletPubkey: string },
): Promise<SongIxBuild> {
  const { data } = await client.post<SongIxBuild>(`/api/sync-deals/${dealId}/pay/build`, body);
  return data;
}

export async function confirmSyncPay(
  dealId: number,
  body: { walletPubkey: string; txSignature: string },
): Promise<SyncDeal> {
  const { data } = await client.post<SyncDeal>(`/api/sync-deals/${dealId}/pay/confirm`, body);
  return data;
}

export async function fetchSongCredits(walletPubkey: string): Promise<SongCredit[]> {
  const { data } = await client.get<SongCredit[]>("/api/song-credits", {
    params: { walletPubkey },
  });
  return data;
}
