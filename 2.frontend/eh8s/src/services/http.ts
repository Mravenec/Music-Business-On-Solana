import axios from "axios";

const TOKEN_KEY = "eh8s.jwt";
const WALLET_KEY = "eh8s.wallet";

/**
 * Persists the access token in sessionStorage (never the URL).
 * Clearing the token also clears the remembered wallet address.
 *
 * @param token compact JWT or null to clear
 */
export function setAccessToken(token: string | null): void {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(WALLET_KEY);
  }
}

/**
 * Remembers the signed-in wallet so the header can paint it on the first frame after refresh.
 * The JWT subject is the account id, so the address has to live beside the token.
 *
 * @param pubkey base58 wallet or null to clear
 */
export function setSignedInWallet(pubkey: string | null): void {
  if (pubkey) {
    sessionStorage.setItem(WALLET_KEY, pubkey);
  } else {
    sessionStorage.removeItem(WALLET_KEY);
  }
}

/**
 * @return stored signed-in wallet or null
 */
export function getSignedInWallet(): string | null {
  return sessionStorage.getItem(WALLET_KEY);
}

/**
 * Header label used by the wallet adapter: first 4, two dots, last 4.
 *
 * @param pubkey base58 wallet
 * @return truncated label such as 7QVK..DUuC
 */
export function formatWalletLabel(pubkey: string): string {
  if (pubkey.length <= 8) return pubkey;
  return `${pubkey.slice(0, 4)}..${pubkey.slice(-4)}`;
}

/**
 * @return stored JWT or null
 */
export function getAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

/** Same-origin Axios client (Vite proxy) with Bearer interceptor. */
export const apiClient = axios.create({ baseURL: "" });

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
