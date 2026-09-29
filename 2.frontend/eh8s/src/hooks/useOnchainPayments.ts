import { useCallback } from "react";
import { useConnection, useWallet } from "@solana/wallet-adapter-react";
import { PublicKey } from "@solana/web3.js";
import axios from "axios";
import { buildSubscribeAcademy, confirmSubscribeAcademy } from "../services/academyOnchainService";
import { buildSubscribeGeographic, confirmSubscribeGeographic } from "../services/royaltyGeoOnchainService";
import {
  deriveAta,
  deriveVaultAuthority,
  sendClaimRoyaltiesTx,
  sendSubscribeGeographicTx,
} from "../services/royaltyGeoTx";
import {
  buildClaimRoyalties,
  buildSettleConcert,
  confirmClaimRoyalties,
  confirmSettleConcert,
  type SettleConcertBuild,
} from "../services/settleClaimOnchainService";
import { sendSettleConcertTx, sendUpsertMusicianProfileTx } from "../services/settleClaimOnchainTx";
import { sendSubscribeAcademyTx } from "../services/subscribeAcademyTx";

/** Connected wallet + RPC connection for one signed step; throws when no wallet is connected. */
function useSigner() {
  const { connection } = useConnection();
  const wallet = useWallet();
  return useCallback(() => {
    if (!wallet.publicKey || !wallet.sendTransaction) {
      throw new Error("Connect your wallet first.");
    }
    return {
      connection,
      payer: wallet.publicKey,
      walletPubkey: wallet.publicKey.toBase58(),
      sendTransaction: wallet.sendTransaction.bind(wallet),
    };
  }, [connection, wallet]);
}

/**
 * Academy plan payment: backend builds subscribe_academy, the wallet signs, the backend confirms over RPC.
 * @returns the confirmed subscription
 */
export function useAcademySubscribeTx() {
  const signer = useSigner();
  return useCallback(
    async (subscriptionId: number, instructorOwner: string) => {
      const s = signer();
      const build = await buildSubscribeAcademy(subscriptionId, { walletPubkey: s.walletPubkey });
      const { signature, academySubscriptionPda } = await sendSubscribeAcademyTx({
        connection: s.connection,
        payer: s.payer,
        programId: new PublicKey(build.programId),
        usdcMint: new PublicKey(build.usdcMint),
        instructorOwner: new PublicKey(instructorOwner),
        dataHex: build.dataHex,
        sendTransaction: s.sendTransaction,
      });
      return confirmSubscribeAcademy(subscriptionId, {
        walletPubkey: s.walletPubkey,
        txSignature: signature,
        academySubscriptionPda,
      });
    },
    [signer],
  );
}

/**
 * Zone payment: backend builds subscribe_geographic, the wallet signs, the backend confirms over RPC.
 * @returns the transaction signature
 */
export function useGeoSubscribeTx() {
  const signer = useSigner();
  return useCallback(
    async (subscriptionId: number, fallbackGeoCode?: string | null) => {
      const s = signer();
      const build = await buildSubscribeGeographic(subscriptionId, { walletPubkey: s.walletPubkey });
      const geoCode = build.geoCode ?? fallbackGeoCode;
      if (!geoCode) throw new Error("Zone code missing");
      const { signature, geographicSubscriptionPda } = await sendSubscribeGeographicTx({
        connection: s.connection,
        payer: s.payer,
        programId: new PublicKey(build.programId),
        usdcMint: new PublicKey(build.usdcMint),
        geoCode,
        dataHex: build.dataHex || "",
        sendTransaction: s.sendTransaction,
      });
      await confirmSubscribeGeographic(subscriptionId, {
        walletPubkey: s.walletPubkey,
        txSignature: signature,
        geographicSubscriptionPda,
      });
      return signature;
    },
    [signer],
  );
}

/**
 * Musician royalty claim from the vault to the musician's USDC account.
 * @returns the transaction signature
 */
export function useClaimRoyaltiesTx() {
  const signer = useSigner();
  return useCallback(
    async (claimId: number, programIdDevnet: string, usdcMintAddress: string) => {
      const s = signer();
      const programId = new PublicKey(programIdDevnet);
      const usdcMint = new PublicKey(usdcMintAddress);
      const build = await buildClaimRoyalties(claimId, {
        walletPubkey: s.walletPubkey,
        vaultUsdcAta: deriveAta(deriveVaultAuthority(programId), usdcMint).toBase58(),
        musicianUsdcAta: deriveAta(s.payer, usdcMint).toBase58(),
      });
      const { signature, musicianProfilePda } = await sendClaimRoyaltiesTx({
        connection: s.connection,
        musician: s.payer,
        programId,
        usdcMint,
        dataHex: build.dataHex,
        sendTransaction: s.sendTransaction,
      });
      await confirmClaimRoyalties(claimId, {
        walletPubkey: s.walletPubkey,
        txSignature: signature,
        claimPda: musicianProfilePda,
      });
      return signature;
    },
    [signer],
  );
}

/**
 * Venue settles one concert: fee to the treasury PDA, pool to the vault, split by on-chain weights.
 * @returns the signature and the build (per-member split shown after paying)
 */
export function useSettleConcertTx() {
  const signer = useSigner();
  return useCallback(
    async (
      settlementId: number,
      fallback: { usdcMint: string; concertId: number },
    ): Promise<{ signature: string; build: SettleConcertBuild }> => {
      const s = signer();
      let build: SettleConcertBuild;
      try {
        build = await buildSettleConcert(settlementId, { walletPubkey: s.walletPubkey });
      } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 409) {
          throw new Error(
            "Could not settle: the band vault is not active — the owner activates it from the band page first.",
          );
        }
        throw err;
      }
      const { signature, concertSettlementPda } = await sendSettleConcertTx({
        connection: s.connection,
        venue: s.payer,
        programId: new PublicKey(build.programId),
        usdcMint: new PublicKey(build.usdcMint || fallback.usdcMint),
        bandId: build.bandId,
        memberWallets: build.memberWallets.map((w) => new PublicKey(w)),
        concertId: build.concertId ?? fallback.concertId,
        dataHex: build.dataHex,
        sendTransaction: s.sendTransaction,
      });
      await confirmSettleConcert(settlementId, {
        walletPubkey: s.walletPubkey,
        txSignature: signature,
        concertSettlementPda,
      });
      return { signature, build };
    },
    [signer],
  );
}

/**
 * Creates or updates the musician profile PDA (instrument + country) with the connected wallet.
 * @returns the transaction signature
 */
export function useMusicianProfileTx() {
  const signer = useSigner();
  return useCallback(
    async (programIdDevnet: string, instrumentCode: number, countryCode: string) => {
      const s = signer();
      const { signature } = await sendUpsertMusicianProfileTx({
        connection: s.connection,
        musician: s.payer,
        programId: new PublicKey(programIdDevnet),
        instrumentCode,
        countryCode,
        sendTransaction: s.sendTransaction,
      });
      return signature;
    },
    [signer],
  );
}
