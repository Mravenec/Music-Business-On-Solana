import { apiClient as client } from "./http";

/** Band row with its on-chain vault columns (JOOQ Band POJO keys). */
export type BandVault = {
  id: number;
  code: string;
  name: string;
  bandVaultPda: string | null;
  vaultTxSignature: string | null;
  vaultActivatedAt: string | null;
  /** JSON array of { musicianProfileId, wallet, bps } in vault member order. */
  sppWeightsJson: string | null;
  weightsTxSignature: string | null;
  weightsSyncedAt: string | null;
};

export type BandVaultMember = {
  musicianProfileId: number;
  wallet: string;
  bps?: number;
};

export type BandVaultBuild = {
  instruction: string;
  programId: string;
  rpcUrl: string;
  ownerWalletPubkey: string;
  bandId: number;
  dataHex: string;
  memberWallets?: string[];
  members: BandVaultMember[];
};

/** Parses the synced weights column (empty when the vault is not active yet). */
export function parseVaultWeights(band: BandVault | null | undefined): BandVaultMember[] {
  if (!band?.sppWeightsJson) return [];
  try {
    const rows = JSON.parse(band.sppWeightsJson) as BandVaultMember[];
    return Array.isArray(rows) ? rows : [];
  } catch {
    return [];
  }
}

export async function fetchBandVault(bandId: number): Promise<BandVault> {
  const { data } = await client.get<BandVault>(`/api/bands/${bandId}/vault`);
  return data;
}

/** Builds create_band for the band members' wallets (owner wallet only). */
export async function buildActivateVault(
  bandId: number,
  body: { walletPubkey: string },
): Promise<BandVaultBuild> {
  const { data } = await client.post<BandVaultBuild>(`/api/bands/${bandId}/vault/build`, body);
  return data;
}

/** Records a DevNet-confirmed create_band (backend verifies the signature over RPC). */
export async function confirmActivateVault(
  bandId: number,
  body: { walletPubkey: string; txSignature: string; bandVaultPda: string },
): Promise<BandVault> {
  if (!body.txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to activate without a DevNet signature");
  }
  const { data } = await client.post<BandVault>(`/api/bands/${bandId}/vault/confirm`, body);
  return data;
}

/** Builds update_spp_weights from the latest closed SPP cycle. */
export async function buildSyncWeights(
  bandId: number,
  body: { walletPubkey: string },
): Promise<BandVaultBuild> {
  const { data } = await client.post<BandVaultBuild>(
    `/api/bands/${bandId}/vault/weights/build`,
    body,
  );
  return data;
}

/** Records a DevNet-confirmed update_spp_weights. */
export async function confirmSyncWeights(
  bandId: number,
  body: { walletPubkey: string; txSignature: string },
): Promise<BandVault> {
  if (!body.txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to sync without a DevNet signature");
  }
  const { data } = await client.post<BandVault>(
    `/api/bands/${bandId}/vault/weights/confirm`,
    body,
  );
  return data;
}
