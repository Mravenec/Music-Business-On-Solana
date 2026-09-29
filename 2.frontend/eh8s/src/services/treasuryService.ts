import { apiClient as client } from "./http";

/** One recorded protocol fee payment (academy, geographic tier, or concert settle). */
export type TreasuryInflow = {
  source: string;
  recordId: number;
  amountUsdc: number;
  txSignature: string;
  at: string | null;
};

/** JOOQ TreasuryWithdrawal POJO keys. */
export type TreasuryWithdrawal = {
  id: number;
  ownerWalletPubkey: string;
  amountUsdc: number;
  txSignature: string;
  createdAt: string;
};

export type OwnerTreasury = {
  ownerWalletPubkey: string;
  programId: string;
  usdcMint: string;
  treasuryPda: string;
  treasuryUsdc: string;
  balanceAtomic: number;
  balanceUsdc: number;
  explorerUrl: string;
  /** True once init_governance is recorded: withdrawals go through proposals. */
  governanceActive?: boolean;
  inflows: TreasuryInflow[];
  withdrawals: TreasuryWithdrawal[];
};

export type WithdrawTreasuryBuild = {
  instruction: string;
  programId: string;
  usdcMint: string;
  ownerWalletPubkey: string;
  amountAtomic: number;
  treasuryPda: string;
  treasuryUsdc: string;
  ownerUsdc: string;
  dataHex: string;
  balanceUsdc: number;
};

/** Live DevNet treasury balance plus recorded inflows and withdrawals (owner wallet only). */
export async function fetchOwnerTreasury(walletPubkey: string): Promise<OwnerTreasury> {
  const { data } = await client.get<OwnerTreasury>("/api/owner/treasury", {
    params: { walletPubkey },
  });
  return data;
}

/** Builds withdraw_treasury for the owner wallet (409 when above the live balance). */
export async function buildWithdrawTreasury(body: {
  walletPubkey: string;
  amountUsdc: number;
}): Promise<WithdrawTreasuryBuild> {
  const { data } = await client.post<WithdrawTreasuryBuild>(
    "/api/owner/treasury/withdraw/build",
    body,
  );
  return data;
}

/** Records a DevNet-confirmed withdraw_treasury (backend verifies the signature over RPC). */
export async function confirmWithdrawTreasury(body: {
  walletPubkey: string;
  amountUsdc: number;
  txSignature: string;
}): Promise<TreasuryWithdrawal> {
  if (!body.txSignature?.trim()) {
    throw new Error("txSignature is required — refusing to record without a DevNet signature");
  }
  const { data } = await client.post<TreasuryWithdrawal>(
    "/api/owner/treasury/withdraw/confirm",
    body,
  );
  return data;
}
