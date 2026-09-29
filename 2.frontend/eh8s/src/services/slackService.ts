import { apiClient as client } from "./http";

export type SlackStatus = {
  configured: boolean;
  mode?: string;
  channelHint?: string;
  note?: string;
  secretPresent?: boolean;
  ownerOnly?: boolean;
  authorized?: boolean;
  ownerWalletPubkey?: string;
  /** True when SLACK_SIGNING_SECRET is set, so Approve / Reject clicks in Slack are accepted. */
  interactive?: boolean;
  /** Path to paste after the public HTTPS host in the Slack app's Interactivity Request URL. */
  interactionsPath?: string;
  interactiveNote?: string;
};

export type SlackDelivery = {
  id: number;
  ownerDecisionId?: number | null;
  agentEventId?: number | null;
  ownerWalletPubkey?: string | null;
  requesterWalletPubkey?: string | null;
  bridgeMode?: string | null;
  channelHint?: string | null;
  payloadPreview: string;
  status: string;
  httpStatus?: number | null;
  errorMessage?: string | null;
  deliveredAt?: string | null;
};

/**
 * Fetches der-dirigent / Slack bridge status for the owner wallet.
 */
export async function fetchSlackStatus(walletPubkey: string): Promise<SlackStatus> {
  const { data } = await client.get<SlackStatus>("/api/slack/status", {
    params: { walletPubkey },
  });
  return data;
}

/**
 * Lists Slack deliveries scoped to the platform owner wallet.
 */
export async function fetchSlackDeliveries(
  walletPubkey: string
): Promise<SlackDelivery[]> {
  const { data } = await client.get<SlackDelivery[]>("/api/slack/deliveries", {
    params: { walletPubkey },
  });
  return data;
}

/**
 * Owner-only notify via webhook or der-dirigent bot (skip when secrets unset).
 */
export async function notifySlack(
  walletPubkey: string,
  text: string,
  ownerDecisionId?: number
) {
  const { data } = await client.post("/api/slack/notify", {
    walletPubkey,
    text,
    ownerDecisionId,
  });
  return data;
}
