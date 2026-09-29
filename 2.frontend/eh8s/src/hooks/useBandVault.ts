import { useCallback, useEffect, useState } from "react";
import { useConnection, useWallet } from "@solana/wallet-adapter-react";
import { PublicKey } from "@solana/web3.js";
import axios from "axios";
import {
  buildActivateVault,
  buildSyncWeights,
  confirmActivateVault,
  confirmSyncWeights,
  fetchBandVault,
  parseVaultWeights,
  type BandVault,
  type BandVaultMember,
} from "../services/bandVaultService";
import { sendCreateBandTx, sendUpdateSppWeightsTx } from "../services/bandVaultTx";

export type BandVaultState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  vault: BandVault | null;
  weights: BandVaultMember[];
  active: boolean;
  /** Build → owner wallet signs create_band → backend confirms over RPC. */
  activate: () => Promise<string>;
  /** Build → owner wallet signs update_spp_weights → backend confirms over RPC. */
  syncWeights: () => Promise<string>;
};

function apiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data && (data as { message?: string }).message) {
      return String((data as { message: string }).message);
    }
    if (status === 401) return "Connect your wallet to sign in first.";
    if (status === 403) return "Only the owner wallet can sign band vault instructions.";
    if (status === 409) return "Not possible yet — check the band vault state and member wallets.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

/**
 * On-chain BandVault of one band: state plus the owner-signed activate / weight-sync flows.
 */
export function useBandVault(bandId: number): BandVaultState {
  const { connection } = useConnection();
  const wallet = useWallet();
  const [vault, setVault] = useState<BandVault | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!Number.isFinite(bandId)) return;
    setLoading(true);
    try {
      setVault(await fetchBandVault(bandId));
      setError(null);
    } catch (err) {
      setError(apiMessage(err, "Could not load the band vault"));
    } finally {
      setLoading(false);
    }
  }, [bandId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const run = useCallback(
    async (job: (owner: PublicKey) => Promise<string>): Promise<string> => {
      if (!wallet.publicKey || !wallet.sendTransaction) {
        throw new Error("Connect the owner wallet first.");
      }
      setBusy(true);
      try {
        const signature = await job(wallet.publicKey);
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

  const activate = useCallback(
    () =>
      run(async (owner) => {
        const walletPubkey = owner.toBase58();
        const build = await buildActivateVault(bandId, { walletPubkey });
        const { signature, bandVaultPda } = await sendCreateBandTx({
          connection,
          owner,
          programId: new PublicKey(build.programId),
          bandId,
          dataHex: build.dataHex,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await confirmActivateVault(bandId, { walletPubkey, txSignature: signature, bandVaultPda });
        return signature;
      }),
    [run, bandId, connection, wallet],
  );

  const syncWeights = useCallback(
    () =>
      run(async (owner) => {
        const walletPubkey = owner.toBase58();
        const build = await buildSyncWeights(bandId, { walletPubkey });
        const signature = await sendUpdateSppWeightsTx({
          connection,
          owner,
          programId: new PublicKey(build.programId),
          bandId,
          dataHex: build.dataHex,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await confirmSyncWeights(bandId, { walletPubkey, txSignature: signature });
        return signature;
      }),
    [run, bandId, connection, wallet],
  );

  return {
    loading,
    busy,
    error,
    vault,
    weights: parseVaultWeights(vault),
    active: Boolean(vault?.bandVaultPda),
    activate,
    syncWeights,
  };
}
