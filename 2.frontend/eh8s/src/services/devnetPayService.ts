import { apiClient as client } from "./http";

export type DevnetPayMarker = {
  id: number;
  kind: string;
  walletPubkey: string;
  amountUsdc?: number | null;
  relatedClaimId?: number | null;
  relatedSubscriptionId?: number | null;
  txSignature?: string | null;
  status: string;
  intendedInstruction: string;
  note?: string | null;
  recordedAt?: string | null;
};

/** Lists DevNet pay/claim markers from MariaDB. */
export async function fetchMarkers(): Promise<DevnetPayMarker[]> {
  const { data } = await client.get<DevnetPayMarker[]>("/api/devnet-pay-markers");
  return data;
}

/** Records an intended or submitted pay/claim marker. */
export async function recordMarker(
  body: Partial<DevnetPayMarker>,
): Promise<DevnetPayMarker> {
  const { data } = await client.post<DevnetPayMarker>("/api/devnet-pay-markers", body);
  return data;
}

/** Records academy subscription DevNet pay. */
export async function recordSubscriptionPay(
  subscriptionId: number,
  walletPubkey: string,
  txSignature?: string,
) {
  const { data } = await client.post(`/api/academy-subscriptions/${subscriptionId}/devnet-pay`, {
    walletPubkey,
    txSignature,
  });
  return data;
}

/** Records pending claim DevNet marker. */
export async function recordClaimPay(
  claimId: number,
  walletPubkey: string,
  txSignature?: string,
) {
  const { data } = await client.post(`/api/pending-claims/${claimId}/devnet-claim`, {
    walletPubkey,
    txSignature,
  });
  return data;
}
