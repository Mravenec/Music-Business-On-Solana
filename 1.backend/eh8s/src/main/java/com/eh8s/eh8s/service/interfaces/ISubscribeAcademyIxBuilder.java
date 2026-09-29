package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Builds subscribe_academy instructions (the backend never signs).
 */
public interface ISubscribeAcademyIxBuilder {

  /**
   * Builds a wallet-ready subscribe_academy instruction description.
   *
   * @param programId DevNet program id
   * @param usdcMint DevNet USDC mint
   * @param rpcUrl DevNet RPC
   * @param payerWallet payer pubkey
   * @param planType on-chain plan code (1 basic, 2 band, 3 pro)
   * @param months months bought (1..12)
   * @param amountUsdc expected total (plan price x months) shown to the wallet
   * @param instructorUsdcAta instructor token account
   * @param payerUsdcAta payer USDC ATA
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> build(
      String programId,
      String usdcMint,
      String rpcUrl,
      String payerWallet,
      int planType,
      int months,
      BigDecimal amountUsdc,
      String instructorUsdcAta,
      String payerUsdcAta);
}
