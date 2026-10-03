import { apiClient, setAccessToken, setSignedInWallet } from "./http";

export type Account = {
  id: number;
  email: string;
  displayName: string;
  role: string;
  countryCode?: string | null;
  walletPubkey?: string | null;
  lastSeenAt?: string | null;
  lastLat?: number | null;
  lastLng?: number | null;
  lastGeoAt?: string | null;
};

export type MusicianProfile = {
  id: number;
  accountId: number;
  instrumentId: number;
  enigmaLevelId: number;
  enigmaScore: number;
  musicianProfilePda?: string | null;
};

export type WalletSession = {
  account: Account;
  musicianProfile?: MusicianProfile | null;
  platformOwner?: boolean;
  studioAdmin?: boolean;
  partner?: boolean;
  protocolFeeBps?: number | null;
  accessToken?: string | null;
};

export type PlatformConfig = {
  network: string;
  ownerWalletPubkey: string;
  protocolFeeBps: number;
  protocolFeePercent: number;
  note?: string;
};

/**
 * Asks the server for a one-time login message.
 */
export async function requestWalletChallenge(walletPubkey: string): Promise<{
  walletPubkey: string;
  nonce: string;
  message: string;
}> {
  const { data } = await apiClient.post<{
    walletPubkey: string;
    nonce: string;
    message: string;
  }>("/api/session/challenge", { walletPubkey });
  return data;
}

/**
 * Upserts MariaDB account identity after the wallet signs the challenge.
 * Do not send role=owner — the API assigns owner only for the platform pubkey.
 */
export async function upsertWalletSession(
  walletPubkey: string,
  signature: string,
  displayName?: string,
  role?: string
): Promise<WalletSession> {
  const body: Record<string, string> = { walletPubkey, signature };
  if (displayName) body.displayName = displayName;
  if (role && role !== "owner") body.role = role;
  const { data } = await apiClient.post<WalletSession>("/api/session/wallet", body);
  rememberSession(data);
  return data;
}

/**
 * Reloads the signed-in studio from the stored JWT. Does not ask the wallet to sign.
 */
export async function fetchCurrentSession(): Promise<WalletSession> {
  const { data } = await apiClient.get<WalletSession>("/api/session/current");
  rememberSession(data);
  return data;
}

/**
 * Loads an existing wallet session.
 */
export async function fetchWalletSession(
  walletPubkey: string
): Promise<WalletSession> {
  const { data } = await apiClient.get<WalletSession>(
    `/api/session/wallet/${encodeURIComponent(walletPubkey)}`
  );
  rememberSession(data);
  return data;
}

/**
 * Stores browser geolocation for the signed-in wallet.
 */
export async function postWalletLocation(
  walletPubkey: string,
  latitude: number,
  longitude: number
): Promise<WalletSession> {
  const { data } = await apiClient.post<WalletSession>(
    `/api/session/wallet/${encodeURIComponent(walletPubkey)}/location`,
    { lastLat: latitude, lastLng: longitude }
  );
  rememberSession(data);
  return data;
}

/**
 * Keeps the JWT and the signed-in wallet address together in sessionStorage.
 *
 * @param data session payload from the API
 */
function rememberSession(data: WalletSession): void {
  if (data.accessToken) setAccessToken(data.accessToken);
  if (data.account?.walletPubkey) setSignedInWallet(data.account.walletPubkey);
}

/**
 * Loads platform owner wallet and protocol fee from MariaDB.
 */
export async function fetchPlatformConfig(): Promise<PlatformConfig> {
  const { data } = await apiClient.get<PlatformConfig>("/api/platform/config");
  return data;
}
