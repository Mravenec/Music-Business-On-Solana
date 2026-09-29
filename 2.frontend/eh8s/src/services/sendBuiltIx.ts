import {
  Connection,
  PublicKey,
  Transaction,
  TransactionInstruction,
} from "@solana/web3.js";

function hexToBytes(hex: string): Uint8Array {
  const clean = hex.startsWith("0x") ? hex.slice(2) : hex;
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

export type BuiltIx = {
  programId: string;
  rpcUrl: string;
  dataHex?: string;
  accounts?: Array<{ name: string; pubkey: string | null; isWritable?: boolean; isSigner?: boolean }>;
};

/**
 * Sends a built Anchor instruction via the connected wallet on DevNet.
 */
export async function sendBuiltIx(opts: {
  build: BuiltIx;
  payer: PublicKey;
  sendTransaction: (
    transaction: Transaction,
    connection: Connection,
    options?: { skipPreflight?: boolean }
  ) => Promise<string>;
}): Promise<string> {
  const connection = new Connection(opts.build.rpcUrl, "confirmed");
  const programId = new PublicKey(opts.build.programId);
  const keys = (opts.build.accounts ?? [])
    .filter((a) => a.pubkey)
    .map((a) => ({
      pubkey: new PublicKey(a.pubkey as string),
      isWritable: Boolean(a.isWritable),
      isSigner: Boolean(a.isSigner),
    }));
  if (!keys.some((k) => k.pubkey.equals(opts.payer))) {
    keys.unshift({ pubkey: opts.payer, isWritable: true, isSigner: true });
  }
  const data = hexToBytes(opts.build.dataHex || "00");
  const ix = new TransactionInstruction({ programId, keys, data: Buffer.from(data) });
  const tx = new Transaction().add(ix);
  tx.feePayer = opts.payer;
  tx.recentBlockhash = (await connection.getLatestBlockhash()).blockhash;
  return opts.sendTransaction(tx, connection, { skipPreflight: false });
}

/** Derives a PDA from UTF-8 seed + payer. */
export function derivePda(
  programId: PublicKey,
  seed: string,
  payer: PublicKey
): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync(
    [Buffer.from(seed), payer.toBuffer()],
    programId
  );
  return pda;
}
