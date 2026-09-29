package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IVenueBookingIxBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the program v0.7.0 venue and booking instructions: {@code register_venue},
 * {@code approve_venue}, {@code propose_booking}, {@code confirm_booking},
 * {@code cancel_booking} and {@code settle_booking}. Every account pubkey is derived here so the
 * React wallet adapter only signs.
 */
@Component
public class VenueBookingIxBuilder implements IVenueBookingIxBuilder {

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
  /** Longest venue name the program stores (bytes). */
  public static final int MAX_VENUE_NAME = 64;
  /** Highest contract type id the program accepts (1..4, same as {@code contract_type}). */
  public static final int MAX_CONTRACT_TYPE = 4;

  /**
   * VenueListing PDA {@code ["venue", venue_wallet, venue_id LE]}.
   *
   * @param venueWallet venue wallet
   * @param venueId MariaDB venue id
   * @param programId program id
   * @return base58 PDA
   */
  public static String listingPda(String venueWallet, long venueId, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("venue"), SolanaPda.decode(venueWallet), SolanaPda.u64le(venueId)),
        programId);
  }

  /**
   * VenueAccessToken PDA {@code ["access", venue_wallet, band_id LE, date_ymd LE]}.
   *
   * @param venueWallet venue wallet
   * @param bandId MariaDB band id
   * @param dateYmd show date as yyyymmdd
   * @param programId program id
   * @return base58 PDA
   */
  public static String accessPda(String venueWallet, long bandId, int dateYmd, String programId) {
    byte[] date = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dateYmd).array();
    return SolanaPda.findProgramAddress(
        List.of(
            SolanaPda.seed("access"),
            SolanaPda.decode(venueWallet),
            SolanaPda.u64le(bandId),
            date),
        programId);
  }

  /**
   * Escrow token account PDA {@code ["escrow", access_token]} (authority = the access token).
   *
   * @param accessPda VenueAccessToken PDA
   * @param programId program id
   * @return base58 PDA
   */
  public static String escrowPda(String accessPda, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("escrow"), SolanaPda.decode(accessPda)), programId);
  }

  /**
   * ConcertSettlement PDA {@code ["concert", venue_wallet, concert_id LE]}.
   *
   * @param venueWallet venue wallet
   * @param concertId on-chain concert id
   * @param programId program id
   * @return base58 PDA
   */
  public static String concertPda(String venueWallet, long concertId, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("concert"), SolanaPda.decode(venueWallet), SolanaPda.u64le(concertId)),
        programId);
  }

  /**
   * BandVault PDA {@code ["band", band_id LE]}.
   *
   * @param bandId MariaDB band id
   * @param programId program id
   * @return base58 PDA
   */
  public static String bandVaultPda(long bandId, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("band"), SolanaPda.u64le(bandId)), programId);
  }

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
  @Override
  public Map<String, Object> buildRegister(
      String programId,
      String rpcUrl,
      String venueWallet,
      long venueId,
      String name,
      String country,
      int capacity,
      int contractType) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("register_venue");
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    ByteBuffer data =
        ByteBuffer.allocate(8 + 8 + 4 + nameBytes.length + 3 + 4 + 1)
            .order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(venueId);
    data.putInt(nameBytes.length);
    data.put(nameBytes);
    data.put(country.getBytes(StandardCharsets.US_ASCII));
    data.putInt(capacity);
    data.put((byte) contractType);

    String listing = listingPda(venueWallet, venueId, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("venue", venueWallet, true, true));
    accounts.add(account("venueListing", listing, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body = payload("register_venue", programId, rpcUrl, disc, data.array());
    body.put("venueWalletPubkey", venueWallet);
    body.put("venueId", venueId);
    body.put("venueListingPda", listing);
    body.put("accounts", accounts);
    body.put(
        "note",
        "Venue wallet signs register_venue: the listing stays pending until the owner or a STAGE"
            + " agent approves it.");
    return body;
  }

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
  @Override
  public Map<String, Object> buildApprove(
      String programId, String rpcUrl, String signerWallet, String listingPda, String agentAccount) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("approve_venue");
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("authority", signerWallet, false, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("venueListing", listingPda, true, false));
    accounts.add(account("agentAuthority", agentAccount, false, false));

    Map<String, Object> body = payload("approve_venue", programId, rpcUrl, disc, disc);
    body.put("signerWalletPubkey", signerWallet);
    body.put("venueListingPda", listingPda);
    body.put("agentAuthorityAccount", agentAccount);
    body.put("accounts", accounts);
    body.put("note", "Owner or STAGE agent signs approve_venue: the venue can now propose shows.");
    return body;
  }

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
  @Override
  public Map<String, Object> buildPropose(
      String programId,
      String rpcUrl,
      String usdcMint,
      String venueWallet,
      long venueId,
      long concertId,
      long bandId,
      int dateYmd,
      long grossAtomic) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("propose_booking");
    ByteBuffer data = ByteBuffer.allocate(8 + 8 + 8 + 4 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(concertId);
    data.putLong(bandId);
    data.putInt(dateYmd);
    data.putLong(grossAtomic);

    String listing = listingPda(venueWallet, venueId, programId);
    String access = accessPda(venueWallet, bandId, dateYmd, programId);
    String escrow = escrowPda(access, programId);
    String venueUsdc = SolanaPda.associatedTokenAddress(venueWallet, usdcMint);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("venue", venueWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("venueListing", listing, false, false));
    accounts.add(account("bandVault", bandVaultPda(bandId, programId), false, false));
    accounts.add(account("venueAccessToken", access, true, false));
    accounts.add(account("usdcMint", usdcMint, false, false));
    accounts.add(account("escrowUsdc", escrow, true, false));
    accounts.add(account("venueUsdc", venueUsdc, true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body = payload("propose_booking", programId, rpcUrl, disc, data.array());
    body.put("venueWalletPubkey", venueWallet);
    body.put("concertId", concertId);
    body.put("bandId", bandId);
    body.put("dateYmd", dateYmd);
    body.put("grossAtomic", grossAtomic);
    body.put("venueListingPda", listing);
    body.put("venueAccessTokenPda", access);
    body.put("escrowPda", escrow);
    body.put("venueUsdcAta", venueUsdc);
    body.put("accounts", accounts);
    body.put(
        "note",
        "Venue signs propose_booking: the gross moves into escrow until a STAGE agent confirms"
            + " and a VAULT agent settles, or the venue cancels while proposed.");
    return body;
  }

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
  @Override
  public Map<String, Object> buildConfirm(
      String programId,
      String rpcUrl,
      String signerWallet,
      String accessPda,
      byte[] contractHash,
      String agentAccount) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("confirm_booking");
    ByteBuffer data = ByteBuffer.allocate(8 + 32);
    data.put(disc);
    data.put(contractHash);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("authority", signerWallet, false, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("venueAccessToken", accessPda, true, false));
    accounts.add(account("agentAuthority", agentAccount, false, false));

    Map<String, Object> body = payload("confirm_booking", programId, rpcUrl, disc, data.array());
    body.put("signerWalletPubkey", signerWallet);
    body.put("venueAccessTokenPda", accessPda);
    body.put("contractHashHex", HexFormat.of().formatHex(contractHash));
    body.put("agentAuthorityAccount", agentAccount);
    body.put("accounts", accounts);
    body.put("note", "Owner or STAGE agent signs confirm_booking: the contract hash is pinned.");
    return body;
  }

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
  @Override
  public Map<String, Object> buildCancel(
      String programId, String rpcUrl, String usdcMint, String venueWallet, String accessPda) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("cancel_booking");
    String escrow = escrowPda(accessPda, programId);
    String venueUsdc = SolanaPda.associatedTokenAddress(venueWallet, usdcMint);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("venue", venueWallet, true, true));
    accounts.add(account("venueAccessToken", accessPda, true, false));
    accounts.add(account("escrowUsdc", escrow, true, false));
    accounts.add(account("venueUsdc", venueUsdc, true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));

    Map<String, Object> body = payload("cancel_booking", programId, rpcUrl, disc, disc);
    body.put("venueWalletPubkey", venueWallet);
    body.put("venueAccessTokenPda", accessPda);
    body.put("escrowPda", escrow);
    body.put("venueUsdcAta", venueUsdc);
    body.put("accounts", accounts);
    body.put("note", "Venue signs cancel_booking: the full escrow returns to the venue.");
    return body;
  }

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
  @Override
  public Map<String, Object> buildSettle(
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
      List<String> memberWallets) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("settle_booking");
    ByteBuffer data = ByteBuffer.allocate(8 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(grossAtomic);
    data.putLong(expensesAtomic);

    String settlement = concertPda(venueWallet, concertId, programId);
    String escrow = escrowPda(accessPda, programId);
    String venueUsdc = SolanaPda.associatedTokenAddress(venueWallet, usdcMint);
    String treasuryUsdc = SubscribeAcademyIxBuilder.treasuryUsdc(programId, usdcMint);
    String vaultUsdc =
        SolanaPda.associatedTokenAddress(
            SolanaPda.findProgramAddress(List.of(SolanaPda.seed("vault")), programId), usdcMint);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("authority", signerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("venueAccessToken", accessPda, true, false));
    accounts.add(account("bandVault", bandVaultPda(bandId, programId), false, false));
    accounts.add(account("concertSettlement", settlement, true, false));
    accounts.add(account("escrowUsdc", escrow, true, false));
    accounts.add(account("venueUsdc", venueUsdc, true, false));
    accounts.add(account("treasuryUsdc", treasuryUsdc, true, false));
    accounts.add(account("vaultUsdc", vaultUsdc, true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));
    accounts.add(account("agentAuthority", agentAccount, false, false));
    List<String> memberProfiles = new ArrayList<>();
    for (int i = 0; i < memberWallets.size(); i++) {
      String profile = SolanaPda.walletPda("musician", memberWallets.get(i), programId);
      memberProfiles.add(profile);
      accounts.add(account("memberProfile" + i, profile, true, false));
    }

    Map<String, Object> body = payload("settle_booking", programId, rpcUrl, disc, data.array());
    body.put("signerWalletPubkey", signerWallet);
    body.put("venueWalletPubkey", venueWallet);
    body.put("venueAccessTokenPda", accessPda);
    body.put("concertSettlementPda", settlement);
    body.put("escrowPda", escrow);
    body.put("agentAuthorityAccount", agentAccount);
    body.put("memberWallets", memberWallets);
    body.put("memberProfilePdas", memberProfiles);
    body.put("grossAtomic", grossAtomic);
    body.put("expensesAtomic", expensesAtomic);
    body.put("accounts", accounts);
    body.put(
        "note",
        "Owner or VAULT agent signs settle_booking: expenses to the venue, fee to treasury, pool"
            + " to the band vault, pending credited per member weight. Settles once.");
    return body;
  }

  private static Map<String, Object> payload(
      String instruction, String programId, String rpcUrl, byte[] disc, byte[] data) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", instruction);
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("discriminatorHex", HexFormat.of().formatHex(disc));
    body.put("dataHex", HexFormat.of().formatHex(data));
    return body;
  }

  private static Map<String, Object> account(
      String name, String pubkey, boolean writable, boolean signer) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("pubkey", pubkey);
    row.put("isWritable", writable);
    row.put("isSigner", signer);
    return row;
  }
}
