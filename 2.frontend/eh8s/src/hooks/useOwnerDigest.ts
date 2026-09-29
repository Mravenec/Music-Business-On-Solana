import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import axios from "axios";
import {
  fetchLatestDigest,
  runDigest,
  type OwnerDigestLatest,
} from "../services/ownerDigestService";

export type OwnerDigestState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  data: OwnerDigestLatest | null;
  walletReady: boolean;
  /** One Claude request from live metrics; refreshes the latest digest. */
  generate: () => Promise<void>;
};

function apiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) {
      return String((data as { message: string }).message);
    }
    if (status === 401) return "Connect your wallet to sign in first.";
    if (status === 403) return "Only the owner wallet can use the AI digest.";
    if (status === 503) return "AI digest unavailable: ANTHROPIC_API_KEY is not set on the server.";
    if (status === 502) return "Claude could not be reached. Try again later.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

/**
 * Owner AI digest: latest stored digest plus the "generate" action (owner wallet only).
 */
export function useOwnerDigest(): OwnerDigestState {
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [data, setData] = useState<OwnerDigestLatest | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setData(null);
      return;
    }
    setLoading(true);
    try {
      setData(await fetchLatestDigest(walletPubkey));
      setError(null);
    } catch (err) {
      setData(null);
      setError(apiMessage(err, "Could not load the digest"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const generate = useCallback(async () => {
    if (!walletPubkey) throw new Error("Connect the owner wallet first.");
    setBusy(true);
    try {
      await runDigest(walletPubkey);
      await refresh();
    } catch (err) {
      throw new Error(apiMessage(err, "Could not generate the digest"));
    } finally {
      setBusy(false);
    }
  }, [walletPubkey, refresh]);

  return { loading, busy, error, data, walletReady: Boolean(walletPubkey), generate };
}
