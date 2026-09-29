package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Builds subscribe_geographic instructions (the backend never signs).
 */
public interface ISubscribeGeographicIxBuilder {

  /**
   * Builds a wallet-ready subscribe_geographic instruction description.
   *
   * @param programId DevNet program id
   * @param usdcMint DevNet USDC mint
   * @param rpcUrl DevNet RPC
   * @param payerWallet payer pubkey
   * @param geoCode zone seed (see {@link #GEO_CODE})
   * @param tier on-chain tier code (1 local .. 5 global)
   * @param months months bought (1..12)
   * @param amountUsdc expected total (tier price x months) shown to the wallet
   * @param payerUsdcAta payer USDC ATA
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> build(
      String programId,
      String usdcMint,
      String rpcUrl,
      String payerWallet,
      String geoCode,
      int tier,
      int months,
      BigDecimal amountUsdc,
      String payerUsdcAta);
}
