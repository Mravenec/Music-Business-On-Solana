import axios from "axios";

/** Spring error JSON carries no reason text, so each screen maps HTTP status to its own words. */
export function statusText(
  err: unknown,
  texts: Partial<Record<number, string>>,
  fallback: string
): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    if (status && texts[status]) return texts[status] as string;
    if (status === 401) return "Connect your wallet to sign in first.";
    if (!err.response) return "Network Error";
  }
  return fallback;
}
