import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { fetchSlackStatus, type SlackStatus } from "../services/slackService";
import { statusText } from "./statusText";

const SLACK_TEXTS: Partial<Record<number, string>> = {
  403: "Only the owner wallet can see the Slack bridge.",
};

export type SlackStatusState = {
  loading: boolean;
  error: string | null;
  status: SlackStatus | null;
  walletReady: boolean;
  refresh: () => Promise<void>;
};

/**
 * Slack bridge status for the connected owner wallet (mode + whether Approve / Reject clicks work).
 */
export function useSlackStatus(): SlackStatusState {
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [status, setStatus] = useState<SlackStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setStatus(null);
      return;
    }
    setLoading(true);
    try {
      setStatus(await fetchSlackStatus(walletPubkey));
      setError(null);
    } catch (err) {
      setStatus(null);
      setError(statusText(err, SLACK_TEXTS, "Could not load the Slack bridge status."));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { loading, error, status, walletReady: Boolean(walletPubkey), refresh };
}
