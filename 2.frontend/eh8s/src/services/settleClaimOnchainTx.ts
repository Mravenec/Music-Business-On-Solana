import {
  Connection,
  PublicKey,
  SystemProgram,
  Transaction,
  TransactionInstruction,
} from "@solana/web3.js";
import { deriveConfigPda, deriveTreasuryUsdc } from "./subscribeAcademyTx";
import { deriveBandVaultPda } from "./bandVaultTx";

export { deriveConfigPda };

const TOKEN_PROGRAM_ID = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
const ASSOCIATED_TOKEN_PROGRAM_ID = new PublicKey(
  "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
);

function hexToBytes(hex: string): Uint8Array {
  const clean = hex.startsWith("0x") ? hex.slice(2) : hex;
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

/** MusicianProfile PDA — seeds ["musician", musician]. Matches ClaimRoyaltiesIxBuilder. */
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

/** Vault authority PDA — seeds ["vault"]. Matches ClaimRoyaltiesIxBuilder. */
export function deriveVaultAuthority(programId: PublicKey): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync([Buffer.from("vault")], programId);
  return pda;
}

/** Standard SPL associated token account for (owner, mint). */
export function deriveAta(owner: PublicKey, mint: PublicKey): PublicKey {
  const [ata] = PublicKey.findProgramAddressSync(
    [owner.toBuffer(), TOKEN_PROGRAM_ID.toBuffer(), mint.toBuffer()],
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  return ata;
}

type SendTx = (
  transaction: Transaction,
  connection: Connection,
  options?: { skipPreflight?: boolean },
) => Promise<string>;

export type ClaimRoyaltiesTxInput = {
  connection: Connection;
  musician: PublicKey;
  programId: PublicKey;
  vaultUsdc: PublicKey;
  musicianUsdc: PublicKey;
  dataHex: string;
  sendTransaction: SendTx;
};

/**
 * Builds and sends a real DevNet claim_royalties transaction via the connected wallet.
 * Account order mirrors ClaimRoyaltiesIxBuilder exactly (musician, config, musicianProfile,
 * vaultAuthority, vaultUsdc, musicianUsdc, tokenProgram).
 */
export async function sendClaimRoyaltiesTx(
  input: ClaimRoyaltiesTxInput,
): Promise<{ signature: string; musicianProfilePda: string }> {
  const musicianProfilePda = deriveMusicianProfilePda(input.programId, input.musician);
  const vaultAuthority = deriveVaultAuthority(input.programId);

  const ix = new TransactionInstruction({
    programId: input.programId,
    keys: [
      { pubkey: input.musician, isSigner: true, isWritable: false },
      { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
      { pubkey: musicianProfilePda, isSigner: false, isWritable: true },
      { pubkey: vaultAuthority, isSigner: false, isWritable: false },
      { pubkey: input.vaultUsdc, isSigner: false, isWritable: true },
      { pubkey: input.musicianUsdc, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data: Buffer.from(hexToBytes(input.dataHex)),
  });

  const { blockhash, lastValidBlockHeight } =
    await input.connection.getLatestBlockhash("confirmed");
  const tx = new Transaction({
    feePayer: input.musician,
    blockhash,
    lastValidBlockHeight,
  }).add(ix);

  const signature = await input.sendTransaction(tx, input.connection, {
    skipPreflight: false,
  });
  if (!signature || !signature.trim()) {
    throw new Error("Wallet returned a blank signature — refusing to continue");
  }
  await input.connection.confirmTransaction(
    { signature, blockhash, lastValidBlockHeight },
    "confirmed",
  );
  return { signature, musicianProfilePda: musicianProfilePda.toBase58() };
}

/**
 * ConcertSettlement PDA — seeds ["concert", venue, concert_id u64 LE]. One settlement per
 * concert id; matches SettleConcertIxBuilder.
 */
export function deriveConcertSettlementPda(
  programId: PublicKey,
  venue: PublicKey,
  concertId: number,
): PublicKey {
  const idLe = new Uint8Array(8);
  new DataView(idLe.buffer).setBigUint64(0, BigInt(concertId), true);
  const [pda] = PublicKey.findProgramAddressSync(
    [Buffer.from("concert"), venue.toBuffer(), idLe],
    programId,
  );
  return pda;
}

async function sendAndConfirm(
  connection: Connection,
  feePayer: PublicKey,
  ix: TransactionInstruction,
  sendTransaction: SendTx,
): Promise<string> {
  const { blockhash, lastValidBlockHeight } =
    await connection.getLatestBlockhash("confirmed");
  const tx = new Transaction({
    feePayer,
    blockhash,
    lastValidBlockHeight,
  }).add(ix);
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

export type SettleConcertTxInput = {
  connection: Connection;
  venue: PublicKey;
  programId: PublicKey;
  usdcMint: PublicKey;
  bandId: number;
  /** BandVault member wallets in vault order (build.memberWallets). */
  memberWallets: PublicKey[];
  concertId: number;
  dataHex: string;
  sendTransaction: SendTx;
};

/**
 * Sends settle_concert on DevNet: fee (config bps) → treasury ATA, pool → vault ATA, and each
 * BandVault member's MusicianProfile pending is credited by weight. Account order mirrors
 * SettleConcertIxBuilder / Anchor SettleConcert; member profiles are remaining accounts.
 */
export async function sendSettleConcertTx(
  input: SettleConcertTxInput,
): Promise<{ signature: string; concertSettlementPda: string }> {
  const concertSettlementPda = deriveConcertSettlementPda(
    input.programId,
    input.venue,
    input.concertId,
  );
  const vaultAuthority = deriveVaultAuthority(input.programId);
  const venueUsdc = deriveAta(input.venue, input.usdcMint);
  const treasuryUsdc = deriveTreasuryUsdc(input.programId, input.usdcMint);
  const vaultUsdc = deriveAta(vaultAuthority, input.usdcMint);
  const memberProfiles = input.memberWallets.map((wallet) => ({
    pubkey: deriveMusicianProfilePda(input.programId, wallet),
    isSigner: false,
    isWritable: true,
  }));
  const ix = new TransactionInstruction({
    programId: input.programId,
    keys: [
      { pubkey: input.venue, isSigner: true, isWritable: true },
      { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
      {
        pubkey: deriveBandVaultPda(input.programId, input.bandId),
        isSigner: false,
        isWritable: false,
      },
      { pubkey: concertSettlementPda, isSigner: false, isWritable: true },
      { pubkey: venueUsdc, isSigner: false, isWritable: true },
      { pubkey: treasuryUsdc, isSigner: false, isWritable: true },
      { pubkey: vaultUsdc, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      ...memberProfiles,
    ],
    data: Buffer.from(hexToBytes(input.dataHex)),
  });
  const signature = await sendAndConfirm(
    input.connection,
    input.venue,
    ix,
    input.sendTransaction,
  );
  return { signature, concertSettlementPda: concertSettlementPda.toBase58() };
}

async function instructionDataHex(
  name: string,
  extra: Uint8Array = new Uint8Array(),
): Promise<string> {
  const enc = new TextEncoder();
  const hash = await crypto.subtle.digest("SHA-256", enc.encode(`global:${name}`));
  const disc = new Uint8Array(hash).slice(0, 8);
  const all = new Uint8Array(disc.length + extra.length);
  all.set(disc, 0);
  all.set(extra, disc.length);
  return Array.from(all)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export type UpsertMusicianTxInput = {
  connection: Connection;
  musician: PublicKey;
  programId: PublicKey;
  instrumentCode: number;
  /** ISO-3166 alpha-3, three uppercase letters. */
  countryCode: string;
  sendTransaction: SendTx;
};

/**
 * Creates (or migrates) the MusicianProfile PDA so later deposit/claim can credit pending USDC.
 * Program v0.6.0 args: instrument_code u8 + country [u8; 3]; the level stays agent-only.
 */
export async function sendUpsertMusicianProfileTx(
  input: UpsertMusicianTxInput,
): Promise<{ signature: string; musicianProfilePda: string }> {
  const musicianProfilePda = deriveMusicianProfilePda(
    input.programId,
    input.musician,
  );
  const country = input.countryCode.trim().toUpperCase();
  if (!/^[A-Z]{3}$/.test(country)) {
    throw new Error("Country must be three letters (ISO-3166 alpha-3), for example MEX");
  }
  const extra = new Uint8Array([
    input.instrumentCode & 255,
    country.charCodeAt(0),
    country.charCodeAt(1),
    country.charCodeAt(2),
  ]);
  const dataHex = await instructionDataHex("upsert_musician_profile", extra);
  const ix = new TransactionInstruction({
    programId: input.programId,
    keys: [
      { pubkey: input.musician, isSigner: true, isWritable: true },
      { pubkey: musicianProfilePda, isSigner: false, isWritable: true },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
    ],
    data: Buffer.from(hexToBytes(dataHex)),
  });
  const signature = await sendAndConfirm(
    input.connection,
    input.musician,
    ix,
    input.sendTransaction,
  );
  return { signature, musicianProfilePda: musicianProfilePda.toBase58() };
}

/** DevNet explorer link for a transaction signature. */
export function explorerTxUrl(signature: string): string {
  return `https://explorer.solana.com/tx/${signature}?cluster=devnet`;
}
