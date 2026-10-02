import { Connection, PublicKey } from "@solana/web3.js";

export type WalletContents = {
  sol: number;
  usdc: number;
};

/**
 * Reads SOL and the configured USDC balance. This is what the wallet holds, not studio earnings.
 *
 * @param connection Solana RPC
 * @param owner base58 wallet
 * @param usdcMint USDC mint
 */
export async function readWalletContents(
  connection: Connection,
  owner: string,
  usdcMint: string
): Promise<WalletContents> {
  const key = new PublicKey(owner);
  const lamports = await connection.getBalance(key);
  const accounts = await connection.getParsedTokenAccountsByOwner(key, {
    mint: new PublicKey(usdcMint),
  });
  const ui = accounts.value[0]?.account.data.parsed?.info?.tokenAmount?.uiAmount;
  return {
    sol: lamports / 1_000_000_000,
    usdc: typeof ui === "number" ? ui : 0,
  };
}
