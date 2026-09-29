package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Builds venue listing / access / escrow / settle instructions (the backend never signs).
 */
public interface IVenueBookingIxBuilder {

  /**
   * Builds {@code register_venue(venue_id, name, country, capacity, contract_type)}; the venue
   * wallet signs and pays the VenueListing rent.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param venueWallet venue wallet (signer)
   * @param venueId MariaDB venue id (PDA seed)
   * @param name venue name (at most 64 bytes)
   * @param country ISO-3166 alpha-3, upper case
   * @param capacity capacity (&gt; 0)
   * @param contractType contract type 1..4
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildRegister(
      String programId,
      String rpcUrl,
      String venueWallet,
      long venueId,
      String name,
      String country,
      int capacity,
      int contractType);

  /**
   * Builds {@code approve_venue()}. {@code agentAccount} is the signer's AgentAuthority PDA, or
   * the program id (Anchor {@code None}) when the owner signs.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param signerWallet owner or STAGE agent (signer)
   * @param listingPda VenueListing PDA
   * @param agentAccount AgentAuthority PDA or program id
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildApprove(
      String programId, String rpcUrl, String signerWallet, String listingPda, String agentAccount);

  /**
   * Builds {@code propose_booking(concert_id, band_id, date_ymd, gross_usdc)}; the venue moves
   * the gross from its USDC ATA into the escrow owned by the new VenueAccessToken.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param venueWallet venue wallet (signer)
   * @param venueId MariaDB venue id (listing seed)
   * @param concertId on-chain concert id (settlement seed)
   * @param bandId MariaDB band id
   * @param dateYmd show date yyyymmdd
   * @param grossAtomic gross in 6-decimal USDC units
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildPropose(
      String programId,
      String rpcUrl,
      String usdcMint,
      String venueWallet,
      long venueId,
      long concertId,
      long bandId,
      int dateYmd,
      long grossAtomic);

  /**
   * Builds {@code confirm_booking(contract_hash)}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param signerWallet owner or STAGE agent (signer)
   * @param accessPda VenueAccessToken PDA
   * @param contractHash SHA-256 of the canonical contract text (32 bytes)
   * @param agentAccount AgentAuthority PDA or program id
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildConfirm(
      String programId,
      String rpcUrl,
      String signerWallet,
      String accessPda,
      byte[] contractHash,
      String agentAccount);

  /**
   * Builds {@code cancel_booking()}: refunds the escrow to the venue and closes both accounts.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param venueWallet venue wallet (signer)
   * @param accessPda VenueAccessToken PDA
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildCancel(
      String programId, String rpcUrl, String usdcMint, String venueWallet, String accessPda);

  /**
   * Builds {@code settle_booking(gross, expenses)} from the escrow. Remaining accounts are the
   * member MusicianProfile PDAs in vault order.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param signerWallet owner or VAULT agent (signer, pays settlement rent)
   * @param venueWallet venue wallet (expenses destination)
   * @param accessPda VenueAccessToken PDA
   * @param bandId MariaDB band id
   * @param concertId on-chain concert id
   * @param grossAtomic escrowed gross (6 decimals)
   * @param expensesAtomic expenses returned to the venue (6 decimals)
   * @param agentAccount AgentAuthority PDA or program id
   * @param memberWallets vault members in vault order
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildSettle(
      String programId,
      String rpcUrl,
      String usdcMint,
      String signerWallet,
      String venueWallet,
      String accessPda,
      long bandId,
      long concertId,
      long grossAtomic,
      long expensesAtomic,
      String agentAccount,
      List<String> memberWallets);
}
