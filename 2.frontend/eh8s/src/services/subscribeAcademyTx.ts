import {
  Connection,
  PublicKey,
  SystemProgram,
  Transaction,
  TransactionInstruction,
} from "@solana/web3.js";

const TOKEN_PROGRAM_ID = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
const ASSOCIATED_TOKEN_PROGRAM_ID = new PublicKey(
  "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
);

/** Derives the AcademySubscription PDA for seeds ["academy_sub", payer] (program v0.9.0). */
export function deriveAcademySubscriptionPda(
  programId: PublicKey,
  payer: PublicKey,
): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync(
    [Buffer.from("academy_sub"), payer.toBuffer()],
    programId,
  );
  return pda;
}

/** Derives an associated token account address. */
export function deriveAta(owner: PublicKey, mint: PublicKey): PublicKey {
  const [ata] = PublicKey.findProgramAddressSync(
    [owner.toBuffer(), TOKEN_PROGRAM_ID.toBuffer(), mint.toBuffer()],
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  return ata;
}

/** Eh8sConfig PDA — seeds ["eh8s", "config"]; pins mint, treasury, and vault on-chain. */
export function deriveConfigPda(programId: PublicKey): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync(
    [Buffer.from("eh8s"), Buffer.from("config")],
    programId,
  );
  return pda;
}

/** Treasury authority PDA — seeds ["treasury"]; owns the protocol fee USDC ATA. */
export function deriveTreasuryPda(programId: PublicKey): PublicKey {
  const [pda] = PublicKey.findProgramAddressSync([Buffer.from("treasury")], programId);
  return pda;
}

/** Protocol fee destination (config.treasury_usdc): ATA of the ["treasury"] PDA. */
export function deriveTreasuryUsdc(programId: PublicKey, mint: PublicKey): PublicKey {
  return deriveAta(deriveTreasuryPda(programId), mint);
}

/**
 * SPL Associated Token "create idempotent" instruction: creates the owner's ATA when missing,
 * no-op when it already exists. The payer funds rent.
 */
export function createAtaIdempotentIx(
  payer: PublicKey,
  owner: PublicKey,
  mint: PublicKey,
): TransactionInstruction {
  return new TransactionInstruction({
    programId: ASSOCIATED_TOKEN_PROGRAM_ID,
    keys: [
      { pubkey: payer, isSigner: true, isWritable: true },
      { pubkey: deriveAta(owner, mint), isSigner: false, isWritable: true },
      { pubkey: owner, isSigner: false, isWritable: false },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    ],
    data: Buffer.from([1]),
  });
}

function hexToBytes(hex: string): Uint8Array {
  const clean = hex.startsWith("0x") ? hex.slice(2) : hex;
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

export type SubscribeAcademyTxInput = {
  connection: Connection;
  payer: PublicKey;
  programId: PublicKey;
  usdcMint: PublicKey;
  instructorOwner: PublicKey;
  dataHex: string;
  /** Wallet adapter sendTransaction */
  sendTransaction: (
    transaction: Transaction,
    connection: Connection,
    options?: { skipPreflight?: boolean },
  ) => Promise<string>;
};

/**
 * Builds and sends a real DevNet subscribe_academy(plan_type, months) transaction via the connected
 * wallet. Renewing extends the same PDA's expires_at.
 */
export async function sendSubscribeAcademyTx(
  input: SubscribeAcademyTxInput,
): Promise<{ signature: string; academySubscriptionPda: string }> {
  const academySubscriptionPda = deriveAcademySubscriptionPda(
    input.programId,
    input.payer,
  );
  const payerUsdc = deriveAta(input.payer, input.usdcMint);
  const treasuryUsdc = deriveTreasuryUsdc(input.programId, input.usdcMint);
  const instructorUsdc = deriveAta(input.instructorOwner, input.usdcMint);

  const keys = [
    { pubkey: input.payer, isSigner: true, isWritable: true },
    { pubkey: deriveConfigPda(input.programId), isSigner: false, isWritable: false },
    { pubkey: academySubscriptionPda, isSigner: false, isWritable: true },
    { pubkey: payerUsdc, isSigner: false, isWritable: true },
    { pubkey: treasuryUsdc, isSigner: false, isWritable: true },
    { pubkey: instructorUsdc, isSigner: false, isWritable: true },
    { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
    { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
  ];

  const ix = new TransactionInstruction({
    programId: input.programId,
    keys,
    data: Buffer.from(hexToBytes(input.dataHex)),
  });

  const { blockhash, lastValidBlockHeight } =
    await input.connection.getLatestBlockhash("confirmed");
  const tx = new Transaction({
    feePayer: input.payer,
    blockhash,
    lastValidBlockHeight,
  }).add(
    createAtaIdempotentIx(input.payer, input.instructorOwner, input.usdcMint),
    ix,
  );

  const signature = await input.sendTransaction(tx, input.connection, {
    skipPreflight: false,
  });
  await input.connection.confirmTransaction(
    { signature, blockhash, lastValidBlockHeight },
    "confirmed",
  );
  return { signature, academySubscriptionPda: academySubscriptionPda.toBase58() };
}
