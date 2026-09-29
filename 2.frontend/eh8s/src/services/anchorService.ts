import { apiClient as client } from "./http";

export type AnchorMetadata = {
  network: string;
  rpcUrl: string;
  programIdDevnet: string;
  programIdMainnet?: string | null;
  anchorProgramName: string;
  anchorScaffoldPath: string;
  anchorIdlVersion: string;
  usdcMint?: string | null;
  squadsMultisig?: string | null;
  ownerWalletPubkey?: string | null;
  protocolFeeBps?: number | null;
  instructions?: string[];
  mainnetDeployRequired?: boolean;
  note?: string;
};

export async function fetchAnchorMetadata(): Promise<AnchorMetadata> {
  const { data } = await client.get<AnchorMetadata>("/api/anchor/metadata");
  return data;
}
