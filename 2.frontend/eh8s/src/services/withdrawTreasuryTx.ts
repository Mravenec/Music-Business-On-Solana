import { Connection, PublicKey, Transaction, TransactionInstruction } from "@solana/web3.js";
import {
  createAtaIdempotentIx,
  deriveAta,
  deriveConfigPda,
  deriveTreasuryPda,
  deriveTreasuryUsdc,
} from "./subscribeAcademyTx";

const TOKEN_PROGRAM_ID = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

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

export type WithdrawTreasuryTxInput = {
  connection: Connection;
  owner: PublicKey;
  programId: PublicKey;
  usdcMint: PublicKey;
  /** Backend build.treasuryUsdc — must match the locally derived PDA ATA. */
  expectedTreasuryUsdc: string;
  dataHex: string;
  sendTransaction: SendTx;
};

/**
 * Sends withdraw_treasury on DevNet. Account order mirrors Anchor WithdrawTreasury:
 * owner, config, treasuryAuthority, treasuryUsdc, ownerUsdc, tokenProgram.
 */
export async function sendWithdrawTreasuryTx(input: WithdrawTreasuryTxInput): Promise<string> {
  const treasuryUsdc = deriveTreasuryUsdc(input.programId, input.usdcMint);
  if (treasuryUsdc.toBase58() !== input.expectedTreasuryUsdc) {
    throw new Error("Treasury account mismatch — refusing to sign");
  }
  const ix = new TransactionInstruction({
    programId: input.programId,
    keys: [
      { pubkey: input.owner, isSigner: true, isWritable: false },
      { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
      { pubkey: deriveTreasuryPda(input.programId), isSigner: false, isWritable: false },
      { pubkey: treasuryUsdc, isSigner: false, isWritable: true },
      { pubkey: deriveAta(input.owner, input.usdcMint), isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data: Buffer.from(hexToBytes(input.dataHex)),
  });
  const { blockhash, lastValidBlockHeight } =
    await input.connection.getLatestBlockhash("confirmed");
  const tx = new Transaction({ feePayer: input.owner, blockhash, lastValidBlockHeight }).add(
    createAtaIdempotentIx(input.owner, input.owner, input.usdcMint),
    ix,
  );
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
