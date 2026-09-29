import { apiClient as client } from "./http";

/**
 * Active Solana client row from MariaDB (BagsCreatorFund dual-network fields).
 */
export type ChainConfig = {
  id: number;
  network: string;
  rpcUrl: string;
  programIdDevnet: string;
  programIdMainnet: string | null;
  usdcMint: string;
  ownerWalletPubkey?: string | null;
  envNetworkKey: string;
  envRpcKey: string;
  envProgramIdDevnetKey: string;
  envProgramIdMainnetKey: string;
  bagsApiBase: string | null;
  watcherUrl: string | null;
  squadsMultisig: string | null;
  protocolFeeBps?: number | null;
  isActive: number;
};

/**
 * Loads the active chain_config from Spring (same origin via Vite proxy).
 */
export async function fetchChainConfig(): Promise<ChainConfig> {
  const { data } = await client.get<ChainConfig>("/api/chain-config");
  return data;
}

/**
 * Program id for the configured network. Mainnet stays unset until a later deploy.
 */
export function activeProgramId(row: ChainConfig): string {
  if (row.network === "mainnet" && row.programIdMainnet) {
    return row.programIdMainnet;
  }
  return row.programIdDevnet;
}
