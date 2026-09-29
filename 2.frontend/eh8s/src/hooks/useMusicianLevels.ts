import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { Connection } from "@solana/web3.js";
import axios from "axios";
import {
  buildUpdateLevel,
  confirmUpdateLevel,
  fetchLevelConsole,
  type LevelConsole,
} from "../services/agentAuthorityService";
import { sendBuiltIx } from "../services/sendBuiltIx";

export type MusicianLevelsState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  console: LevelConsole | null;
  walletReady: boolean;
  /** Build → owner / pedagogical agent signs update_musician_level → backend confirms over RPC. */
  setLevel: (musicianProfileId: number, newLevel: number) => Promise<string>;
};

function apiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) {
      return String((data as { message: string }).message);
    }
    if (status === 401) return "Connect your wallet to sign in first.";
    if (status === 403) return "Only the owner or the NEXUS agent wallet can change levels.";
    if (status === 404) return "That musician does not exist.";
    if (status === 409) return "This musician has no wallet yet, or the signature was reused.";
    if (status === 400) return "The level change could not be verified on DevNet.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

/**
 * Enigma level console: the owner or a live pedagogical (0x01) agent moves a musician's level
 * on-chain; musicians can never set their own level.
 */
export function useMusicianLevels(): MusicianLevelsState {
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [data, setData] = useState<LevelConsole | null>(null);
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
      setData(await fetchLevelConsole(walletPubkey));
      setError(null);
    } catch (err) {
      setData(null);
      setError(apiMessage(err, "Could not load the level console"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const setLevel = useCallback(
    async (musicianProfileId: number, newLevel: number) => {
      if (!wallet.publicKey || !wallet.sendTransaction) {
        throw new Error("Connect the owner or NEXUS wallet first.");
      }
      const signer = wallet.publicKey;
      const body = { walletPubkey: signer.toBase58(), musicianProfileId, newLevel };
      setBusy(true);
      try {
        const build = await buildUpdateLevel(body);
        const signature = await sendBuiltIx({
          build,
          payer: signer,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await new Connection(build.rpcUrl, "confirmed").confirmTransaction(signature, "confirmed");
        await confirmUpdateLevel({ ...body, txSignature: signature });
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
    console: data,
    walletReady: Boolean(walletPubkey),
    setLevel,
  };
}
