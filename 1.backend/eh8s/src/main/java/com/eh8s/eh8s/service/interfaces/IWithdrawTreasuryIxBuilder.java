package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Builds withdraw_treasury instructions (the backend never signs).
 */
public interface IWithdrawTreasuryIxBuilder {

  /**
   * Builds withdraw_treasury: treasury PDA ATA to the owner's USDC ATA.
   *
   * @param programId DevNet program id
   * @param usdcMint configured USDC mint
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param amountUsdc amount in USDC (6-decimal atomic on-chain)
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> build(
      String programId, String usdcMint, String rpcUrl, String ownerWallet, BigDecimal amountUsdc);
}
