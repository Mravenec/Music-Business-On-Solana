import axios from "axios";

const TOKEN_KEY = "eh8s.jwt";

/**
 * Persists the access token in sessionStorage (never the URL).
 *
 * @param token compact JWT or null to clear
 */
export function setAccessToken(token: string | null): void {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
  }
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
