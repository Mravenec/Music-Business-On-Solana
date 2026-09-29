package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Builds claim_royalties instructions (the backend never signs).
 */
public interface IClaimRoyaltiesIxBuilder {

  /**
   * Builds a wallet-ready claim_royalties instruction description.
   *
   * @param programId DevNet program id
   * @param usdcMint DevNet USDC mint
   * @param rpcUrl DevNet RPC
   * @param musicianWallet musician signer pubkey
   * @param amountUsdc claim amount in human USDC
   * @param vaultUsdcAta vault token account
   * @param musicianUsdcAta musician USDC ATA
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> build(
      String programId,
      String usdcMint,
      String rpcUrl,
      String musicianWallet,
      BigDecimal amountUsdc,
      String vaultUsdcAta,
      String musicianUsdcAta);
}
