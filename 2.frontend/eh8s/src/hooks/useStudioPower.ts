import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@solana/wallet-adapter-react";
import { Connection } from "@solana/web3.js";
import axios from "axios";
import {
  buildGrantStudioRole,
  buildRevokeStudioRole,
  buildSetPartnerShare,
  confirmGrantStudioRole,
  confirmRevokeStudioRole,
  confirmSetPartnerShare,
  fetchStudioPowerConsole,
  type PartnerShare,
  type StudioPowerConsole,
  type StudioRoleGrant,
} from "../services/studioPowerService";
import { sendBuiltIx } from "../services/sendBuiltIx";

export type StudioPowerState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  console: StudioPowerConsole | null;
  walletReady: boolean;
  activeAdmins: StudioRoleGrant[];
  livePartnerShares: PartnerShare[];
  refresh: () => Promise<void>;
  grantAdmin: (adminWalletPubkey: string) => Promise<string>;
  revokeAdmin: (adminWalletPubkey: string) => Promise<string>;
  setPartnerShare: (input: {
    partnerWalletPubkey: string;
    shareBps: number;
    startsAt?: number;
    endsAt?: number;
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
    if (status === 403) return "Only the principal wallet can sign studio power.";
    if (status === 409) {
      return "This partner's share is signed on-chain. Sign set_partner_share with the principal wallet to change it.";
    }
    if (status === 400) return "The instruction could not be verified on DevNet.";
    return err.message || fallback;
  }
  return err instanceof Error ? err.message : fallback;
}

function isActiveGrant(row: StudioRoleGrant): boolean {
  return !row.revokedAt && row.role?.toLowerCase() === "studio_admin";
}

function isLiveShare(row: PartnerShare): boolean {
  if (row.shareBps == null || row.shareBps <= 0) return false;
  if (row.endedAt == null) return true;
  if (typeof row.endedAt === "string") {
    return Date.parse(row.endedAt) > Date.now();
  }
  return true;
}

/**
 * Principal studio power: console mirrors plus wallet-signed grant / revoke / set_partner_share.
 */
export function useStudioPower(): StudioPowerState {
  const wallet = useWallet();
  const walletPubkey = wallet.publicKey?.toBase58() ?? null;
  const [consoleView, setConsoleView] = useState<StudioPowerConsole | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setConsoleView(null);
      return;
    }
    setLoading(true);
    try {
      setConsoleView(await fetchStudioPowerConsole(walletPubkey));
      setError(null);
    } catch (err) {
      setConsoleView(null);
      setError(apiMessage(err, "Could not load studio power"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const signAndConfirm = useCallback(
    async (
      build: Awaited<ReturnType<typeof buildGrantStudioRole>>,
      confirm: (txSignature: string) => Promise<unknown>,
    ) => {
      if (!wallet.publicKey || !wallet.sendTransaction) {
        throw new Error("Connect the principal wallet first.");
      }
      const owner = wallet.publicKey;
      setBusy(true);
      try {
        const signature = await sendBuiltIx({
          build,
          payer: owner,
          sendTransaction: wallet.sendTransaction.bind(wallet),
        });
        await new Connection(build.rpcUrl, "confirmed").confirmTransaction(signature, "confirmed");
        await confirm(signature);
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

  const grantAdmin = useCallback(
    async (adminWalletPubkey: string) => {
      if (!wallet.publicKey) throw new Error("Connect the principal wallet first.");
      const body = {
        walletPubkey: wallet.publicKey.toBase58(),
        adminWalletPubkey: adminWalletPubkey.trim(),
      };
      const build = await buildGrantStudioRole(body);
      return signAndConfirm(build, (txSignature) => confirmGrantStudioRole({ ...body, txSignature }));
    },
    [wallet.publicKey, signAndConfirm],
  );

  const revokeAdmin = useCallback(
    async (adminWalletPubkey: string) => {
      if (!wallet.publicKey) throw new Error("Connect the principal wallet first.");
      const body = {
        walletPubkey: wallet.publicKey.toBase58(),
        adminWalletPubkey: adminWalletPubkey.trim(),
      };
      const build = await buildRevokeStudioRole(body);
      return signAndConfirm(build, (txSignature) => confirmRevokeStudioRole({ ...body, txSignature }));
    },
    [wallet.publicKey, signAndConfirm],
  );

  const setPartnerShare = useCallback(
    async (input: {
      partnerWalletPubkey: string;
      shareBps: number;
      startsAt?: number;
      endsAt?: number;
    }) => {
      if (!wallet.publicKey) throw new Error("Connect the principal wallet first.");
      const body = {
        walletPubkey: wallet.publicKey.toBase58(),
        partnerWalletPubkey: input.partnerWalletPubkey.trim(),
        shareBps: input.shareBps,
        startsAt: input.startsAt,
        endsAt: input.endsAt,
      };
      const build = await buildSetPartnerShare(body);
      return signAndConfirm(build, (txSignature) => confirmSetPartnerShare({ ...body, txSignature }));
    },
    [wallet.publicKey, signAndConfirm],
  );

  const studioRoles = consoleView?.studioRoles ?? [];
  const partnerShares = consoleView?.partnerShares ?? [];

  return {
    loading,
    busy,
    error,
    console: consoleView,
    walletReady: Boolean(walletPubkey),
    activeAdmins: studioRoles.filter(isActiveGrant),
    livePartnerShares: partnerShares.filter(isLiveShare),
    refresh,
    grantAdmin,
    revokeAdmin,
    setPartnerShare,
  };
}

/** Reads Spring / Axios error text; maps partner-mutate 409 to the on-chain sign hint. */
export function studioPowerApiMessage(err: unknown, fallback: string): string {
  return apiMessage(err, fallback);
}
