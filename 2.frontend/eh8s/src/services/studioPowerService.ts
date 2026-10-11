import { apiClient as client } from "./http";
import type { BuiltIx } from "./sendBuiltIx";

/** JOOQ StudioRoleGrant POJO keys (confirmed grant_studio_role / revoke_studio_role). */
export type StudioRoleGrant = {
  id: number;
  walletPubkey: string;
  role: string;
  grantedAt: string | number[] | null;
  revokedAt?: string | number[] | null;
  txSignature: string;
  pda: string;
};

/** JOOQ PartnerShare POJO keys (confirmed set_partner_share). */
export type PartnerShare = {
  id: number;
  walletPubkey: string;
  shareBps: number;
  startedAt: string | number[] | null;
  endedAt?: string | number[] | null;
  txSignature: string;
  pda: string;
};

export type StudioPowerConsole = {
  programId: string;
  ownerWalletPubkey: string;
  studioRoles: StudioRoleGrant[];
  partnerShares: PartnerShare[];
};

export type StudioPowerBuild = BuiltIx & {
  instruction: string;
  note?: string;
  studioRolePda?: string;
  partnerSharePda?: string;
  partnerBookPda?: string;
};

function requireSignature(txSignature: string) {
  if (!txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to record without a DevNet signature");
  }
}

/** Principal console: confirmed studio admin grants and partner share mirrors. */
export async function fetchStudioPowerConsole(walletPubkey: string): Promise<StudioPowerConsole> {
  const { data } = await client.get<StudioPowerConsole>("/api/studio-power/console", {
    params: { walletPubkey },
  });
  return data;
}

/** Builds grant_studio_role for the principal wallet. */
export async function buildGrantStudioRole(body: {
  walletPubkey: string;
  adminWalletPubkey: string;
}): Promise<StudioPowerBuild> {
  const { data } = await client.post<StudioPowerBuild>("/api/studio-power/grant/build", body);
  return data;
}

/** Confirms grant_studio_role on DevNet and records the mirror. */
export async function confirmGrantStudioRole(body: {
  walletPubkey: string;
  adminWalletPubkey: string;
  txSignature: string;
}): Promise<StudioRoleGrant> {
  requireSignature(body.txSignature);
  const { data } = await client.post<StudioRoleGrant>("/api/studio-power/grant/confirm", body);
  return data;
}

/** Builds revoke_studio_role for the principal wallet. */
export async function buildRevokeStudioRole(body: {
  walletPubkey: string;
  adminWalletPubkey: string;
}): Promise<StudioPowerBuild> {
  const { data } = await client.post<StudioPowerBuild>("/api/studio-power/revoke/build", body);
  return data;
}

/** Confirms revoke_studio_role on DevNet and records the mirror. */
export async function confirmRevokeStudioRole(body: {
  walletPubkey: string;
  adminWalletPubkey: string;
  txSignature: string;
}): Promise<StudioRoleGrant> {
  requireSignature(body.txSignature);
  const { data } = await client.post<StudioRoleGrant>("/api/studio-power/revoke/confirm", body);
  return data;
}

/** Builds set_partner_share for the principal wallet. */
export async function buildSetPartnerShare(body: {
  walletPubkey: string;
  partnerWalletPubkey: string;
  shareBps: number;
  startsAt?: number;
  endsAt?: number;
}): Promise<StudioPowerBuild> {
  const { data } = await client.post<StudioPowerBuild>("/api/studio-power/partner-share/build", body);
  return data;
}

/** Confirms set_partner_share on DevNet and records the mirror. */
export async function confirmSetPartnerShare(body: {
  walletPubkey: string;
  partnerWalletPubkey: string;
  shareBps: number;
  startsAt?: number;
  endsAt?: number;
  txSignature: string;
}): Promise<PartnerShare> {
  requireSignature(body.txSignature);
  const { data } = await client.post<PartnerShare>("/api/studio-power/partner-share/confirm", body);
  return data;
}
