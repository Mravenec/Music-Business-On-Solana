import {
  Connection,
  PublicKey,
  SystemProgram,
  Transaction,
  TransactionInstruction,
} from "@solana/web3.js";
import { deriveConfigPda } from "./subscribeAcademyTx";

type SendTx = (
  transaction: Transaction,
  connection: Connection,
  options?: { skipPreflight?: boolean },
) => Promise<string>;

function hexToBytes(hex: string): Uint8Array {
  const clean = hex.startsWith("0x") ? hex.slice(2) : hex;
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

/** BandVault PDA — seeds ["band", band_id u64 LE]. Matches BandVaultIxBuilder. */
export function deriveBandVaultPda(programId: PublicKey, bandId: number): PublicKey {
  const idLe = new Uint8Array(8);
  new DataView(idLe.buffer).setBigUint64(0, BigInt(bandId), true);
  const [pda] = PublicKey.findProgramAddressSync([Buffer.from("band"), idLe], programId);
  return pda;
}

export type BandVaultTxInput = {
  connection: Connection;
  owner: PublicKey;
  programId: PublicKey;
  bandId: number;
  dataHex: string;
  sendTransaction: SendTx;
};

async function sendOwnerIx(
  input: BandVaultTxInput,
  keys: TransactionInstruction["keys"],
): Promise<string> {
  const ix = new TransactionInstruction({
    programId: input.programId,
    keys,
    data: Buffer.from(hexToBytes(input.dataHex)),
  });
  const { blockhash, lastValidBlockHeight } =
    await input.connection.getLatestBlockhash("confirmed");
  const tx = new Transaction({ feePayer: input.owner, blockhash, lastValidBlockHeight }).add(ix);
  const signature = await input.sendTransaction(tx, input.connection, { skipPreflight: false });
  if (!signature || !signature.trim()) {
    throw new Error("Wallet returned a blank signature — refusing to continue");
  }
  await input.connection.confirmTransaction(
    { signature, blockhash, lastValidBlockHeight },
    "confirmed",
  );
  return signature;
}

/**
 * Sends create_band on DevNet (owner signs and pays the BandVault rent).
 * Account order mirrors Anchor CreateBand: owner, config, bandVault, systemProgram.
 */
export async function sendCreateBandTx(
  input: BandVaultTxInput,
): Promise<{ signature: string; bandVaultPda: string }> {
  const bandVault = deriveBandVaultPda(input.programId, input.bandId);
  const signature = await sendOwnerIx(input, [
    { pubkey: input.owner, isSigner: true, isWritable: true },
    { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
    { pubkey: bandVault, isSigner: false, isWritable: true },
    { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
  ]);
  return { signature, bandVaultPda: bandVault.toBase58() };
}

/**
 * Sends update_spp_weights on DevNet. Account order mirrors Anchor UpdateSppWeights:
 * owner, config, bandVault.
 */
export async function sendUpdateSppWeightsTx(input: BandVaultTxInput): Promise<string> {
  return sendOwnerIx(input, [
    { pubkey: input.owner, isSigner: true, isWritable: false },
    { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
    {
      pubkey: deriveBandVaultPda(input.programId, input.bandId),
      isSigner: false,
      isWritable: true,
    },
  ]);
}
