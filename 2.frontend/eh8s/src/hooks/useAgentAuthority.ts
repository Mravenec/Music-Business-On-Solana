import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { Connection } from "@solana/web3.js";
import axios from "axios";
import {
  buildAuthorizeAgent,
  confirmAuthorizeAgent,
  fetchAgentRoster,
  type AgentAuthority,
  type AgentRoster,
} from "../services/agentAuthorityService";
import { sendBuiltIx } from "../services/sendBuiltIx";

export type AgentAuthorityState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  roster: AgentRoster | null;
  walletReady: boolean;
  /** Latest confirmed on-chain grant for an agent code, if any. */
  latestFor: (agentCode: string) => AgentAuthority | null;
  /** Build → owner wallet signs authorize_agent → backend confirms over RPC. */
  authorize: (input: {
    agentCode: string;
    agentWalletPubkey: string;
    permissions: number;
  }) => Promise<string>;
};

function apiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) {
      return String((data as { message: string }).message);
    }
    if (status === 401) return "Connect your wallet to sign in first.";
    if (status === 403) return "Only the owner wallet can authorize agents.";
    if (status === 409) return "That signature was already recorded.";
    if (status === 400) return "The authorization could not be verified on DevNet.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

/**
 * Owner agent roster plus the wallet-signed authorize_agent (grant or revoke permission bits).
 */
export function useAgentAuthority(): AgentAuthorityState {
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [roster, setRoster] = useState<AgentRoster | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setRoster(null);
      return;
    }
    setLoading(true);
    try {
      setRoster(await fetchAgentRoster(walletPubkey));
      setError(null);
    } catch (err) {
      setRoster(null);
      setError(apiMessage(err, "Could not load the agents"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const latestFor = useCallback(
    (agentCode: string): AgentAuthority | null => {
      if (!roster) return null;
      const agent = roster.agents.find((a) => a.code === agentCode);
      if (!agent) return null;
      const rows = roster.authorizations.filter((r) => r.opsAgentId === agent.id);
      return rows.length ? rows.reduce((a, b) => (a.id > b.id ? a : b)) : null;
    },
    [roster],
  );

  const authorize = useCallback(
    async (input: { agentCode: string; agentWalletPubkey: string; permissions: number }) => {
      if (!wallet.publicKey || !wallet.sendTransaction) {
        throw new Error("Connect the owner wallet first.");
      }
      const owner = wallet.publicKey;
      const body = { walletPubkey: owner.toBase58(), ...input };
      setBusy(true);
      try {
        const build = await buildAuthorizeAgent(body);
        const signature = await sendBuiltIx({
          build,
          payer: owner,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await new Connection(build.rpcUrl, "confirmed").confirmTransaction(signature, "confirmed");
        await confirmAuthorizeAgent({ ...body, txSignature: signature });
        await refresh();
        return signature;
      } catch (err) {
        throw new Error(apiMessage(err, "Wallet transaction failed"));
      } finally {
        setBusy(false);
      }
    },
    [wallet, refresh],
  );

  return {
    loading,
    busy,
    error,
    roster,
    walletReady: Boolean(walletPubkey),
    latestFor,
    authorize,
  };
}
