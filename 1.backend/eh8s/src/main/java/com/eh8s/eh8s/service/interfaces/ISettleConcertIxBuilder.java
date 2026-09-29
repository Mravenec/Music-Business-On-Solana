package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Builds settle_concert instructions (the backend never signs).
 */
public interface ISettleConcertIxBuilder {

  /**
   * Builds a wallet-ready settle_concert(concert_id, gross, expenses). The program computes
   * net = gross − expenses, the fee from {@code Eh8sConfig.protocol_fee_bps}, and each member
   * share from the BandVault weights; this builder mirrors that math so the UI can show it.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param venueWallet venue signer pubkey
   * @param ownerWallet treasury owner pubkey
   * @param bandId MariaDB band id (BandVault PDA seed)
   * @param memberWallets band vault members in vault order (remaining accounts)
   * @param weightsBps synced weights in vault order
   * @param concertId MariaDB concert id (settlement PDA seed; settles once)
   * @param grossUsdc venue gross
   * @param expensesUsdc expenses kept by the venue
   * @param protocolFeeBps protocol fee in basis points (same value as on-chain config)
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> build(
      String programId,
      String rpcUrl,
      String usdcMint,
      String venueWallet,
      String ownerWallet,
      long bandId,
      List<String> memberWallets,
      List<Integer> weightsBps,
      long concertId,
      BigDecimal grossUsdc,
      BigDecimal expensesUsdc,
      int protocolFeeBps);
}
