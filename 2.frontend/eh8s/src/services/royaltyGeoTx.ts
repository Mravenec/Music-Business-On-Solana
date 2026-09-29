import {
  Connection,
  PublicKey,
  SystemProgram,
  Transaction,
  TransactionInstruction,
} from "@solana/web3.js";
import {
  createAtaIdempotentIx,
  deriveAta,
  deriveConfigPda,
  deriveTreasuryUsdc,
} from "./subscribeAcademyTx";

export { deriveAta, deriveConfigPda };

const TOKEN_PROGRAM_ID = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

function hexToBytes(hex: string): Uint8Array {
  const clean = hex.startsWith("0x") ? hex.slice(2) : hex;
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

/** GeographicSubscription PDA — seeds ["geo_sub", payer, geo_code] (program v0.9.0). */
export function deriveGeoSubscriptionPda(
  programId: PublicKey,
  payer: PublicKey,
  geoCode: string,
): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync(
    [Buffer.from("geo_sub"), payer.toBuffer(), Buffer.from(geoCode)],
    programId,
  );
  return pda;
}

/** MusicianProfile PDA — seeds ["musician", musician]. */
export function deriveMusicianProfilePda(
  programId: PublicKey,
  musician: PublicKey,
): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync(
    [Buffer.from("musician"), musician.toBuffer()],
    programId,
  );
  return pda;
}

/** Vault authority PDA — seeds ["vault"]. */
export function deriveVaultAuthority(programId: PublicKey): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync([Buffer.from("vault")], programId);
  return pda;
}

type SendTx = (
  transaction: Transaction,
  connection: Connection,
  options?: { skipPreflight?: boolean },
) => Promise<string>;

async function sendAndConfirm(
  connection: Connection,
  feePayer: PublicKey,
  ix: TransactionInstruction | TransactionInstruction[],
  sendTransaction: SendTx,
): Promise<string> {
  const { blockhash, lastValidBlockHeight } =
    await connection.getLatestBlockhash("confirmed");
  const tx = new Transaction({
    feePayer,
    blockhash,
    lastValidBlockHeight,
  }).add(...(Array.isArray(ix) ? ix : [ix]));
  const signature = await sendTransaction(tx, connection, { skipPreflight: false });
  if (!signature || !signature.trim()) {
    throw new Error("Wallet returned a blank signature — refusing to continue");
  }
  await connection.confirmTransaction(
    { signature, blockhash, lastValidBlockHeight },
    "confirmed",
  );
  return signature;
}

export type SubscribeGeographicTxInput = {
  connection: Connection;
  payer: PublicKey;
  programId: PublicKey;
  usdcMint: PublicKey;
  /** Zone seed returned by the build (same bytes as in dataHex). */
  geoCode: string;
  dataHex: string;
  sendTransaction: SendTx;
};

/**
 * Builds and sends a real DevNet subscribe_geographic(geo_code, tier, months) transaction via the
 * connected wallet.
 */
export async function sendSubscribeGeographicTx(
  input: SubscribeGeographicTxInput,
): Promise<{ signature: string; geographicSubscriptionPda: string }> {
  const geographicSubscriptionPda = deriveGeoSubscriptionPda(
    input.programId,
    input.payer,
    input.geoCode,
  );
  const payerUsdc = deriveAta(input.payer, input.usdcMint);
  const treasuryUsdc = deriveTreasuryUsdc(input.programId, input.usdcMint);

  const ix = new TransactionInstruction({
    programId: input.programId,
    keys: [
      { pubkey: input.payer, isSigner: true, isWritable: true },
      { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
      { pubkey: geographicSubscriptionPda, isSigner: false, isWritable: true },
      { pubkey: payerUsdc, isSigner: false, isWritable: true },
      { pubkey: treasuryUsdc, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
    ],
    data: Buffer.from(hexToBytes(input.dataHex)),
  });

  const signature = await sendAndConfirm(
    input.connection,
    input.payer,
    ix,
    input.sendTransaction,
  );
  return {
    signature,
    geographicSubscriptionPda: geographicSubscriptionPda.toBase58(),
  };
}

export type ClaimRoyaltiesTxInput = {
  connection: Connection;
  musician: PublicKey;
  programId: PublicKey;
  usdcMint: PublicKey;
  /** Anchor discriminator + args hex; when omitted, claim_royalties discriminator only. */
  dataHex?: string;
  sendTransaction: SendTx;
};

/**
 * Builds and sends a real DevNet claim_royalties transaction via the connected wallet.
 * When dataHex is omitted, computes the Anchor discriminator client-side.
 */
export async function sendClaimRoyaltiesTx(
  input: ClaimRoyaltiesTxInput,
): Promise<{ signature: string; musicianProfilePda: string }> {
  const musicianProfilePda = deriveMusicianProfilePda(
    input.programId,
    input.musician,
  );
  const vaultAuthority = deriveVaultAuthority(input.programId);
  const vaultUsdc = deriveAta(vaultAuthority, input.usdcMint);
  const musicianUsdc = deriveAta(input.musician, input.usdcMint);

  const dataHex =
    input.dataHex && input.dataHex.trim()
      ? input.dataHex
      : await claimRoyaltiesDataHex();

  const ix = new TransactionInstruction({
    programId: input.programId,
    keys: [
      { pubkey: input.musician, isSigner: true, isWritable: true },
      { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
      { pubkey: musicianProfilePda, isSigner: false, isWritable: true },
      { pubkey: vaultAuthority, isSigner: false, isWritable: false },
      { pubkey: vaultUsdc, isSigner: false, isWritable: true },
      { pubkey: musicianUsdc, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data: Buffer.from(hexToBytes(dataHex)),
  });

  const signature = await sendAndConfirm(
    input.connection,
    input.musician,
    [createAtaIdempotentIx(input.musician, input.musician, input.usdcMint), ix],
    input.sendTransaction,
  );
  return { signature, musicianProfilePda: musicianProfilePda.toBase58() };
}

/** Computes Anchor discriminator hex for claim_royalties (Web Crypto). */
async function claimRoyaltiesDataHex(): Promise<string> {
  const enc = new TextEncoder();
  const hash = await crypto.subtle.digest(
    "SHA-256",
    enc.encode("global:claim_royalties"),
  );
  const bytes = new Uint8Array(hash).slice(0, 8);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}
