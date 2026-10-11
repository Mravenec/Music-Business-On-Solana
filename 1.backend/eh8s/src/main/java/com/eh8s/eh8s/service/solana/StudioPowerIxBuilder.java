package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IStudioPowerIxBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the program v0.11.0 studio power instructions (owner-only, refused on chain once
 * governance exists) and derives their PDAs. Account order matches the Anchor contexts
 * {@code GrantStudioRole}, {@code RevokeStudioRole} and {@code SetPartnerShare}.
 */
@Component
public class StudioPowerIxBuilder implements IStudioPowerIxBuilder {

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";

  /** {@code StudioRole.role} value for a studio admin; 0 means revoked. */
  public static final int STUDIO_ROLE_ADMIN = 1;
  /** Total studio share, in basis points, that overlapping partner windows may reach. */
  public static final int MAX_TOTAL_BPS = 10_000;
  /** Most partner windows the {@code ["partner_book"]} aggregate holds. */
  public static final int MAX_PARTNERS = 16;

  /**
   * Studio role PDA {@code ["studio_role", wallet]}.
   *
   * @param programId base58 program id
   * @param wallet base58 wallet holding (or that held) the role
   * @return base58 PDA
   */
  public static String studioRolePda(String programId, String wallet) {
    return SolanaPda.walletPda("studio_role", wallet, programId);
  }

  /**
   * Partner share PDA {@code ["partner_share", wallet]}.
   *
   * @param programId base58 program id
   * @param wallet base58 partner wallet
   * @return base58 PDA
   */
  public static String partnerSharePda(String programId, String wallet) {
    return SolanaPda.walletPda("partner_share", wallet, programId);
  }

  /**
   * Partner book PDA {@code ["partner_book"]} (every live window, sum capped at 10000 bps).
   *
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String partnerBookPda(String programId) {
    return SolanaPda.findProgramAddress(List.of(SolanaPda.seed("partner_book")), programId);
  }

  /**
   * Builds {@code grant_studio_role(wallet)}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param adminWallet wallet receiving studio_admin
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildGrantStudioRole(
      String programId, String rpcUrl, String ownerWallet, String adminWallet) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("grant_studio_role");
    String rolePda = studioRolePda(programId, adminWallet);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("studioRole", rolePda, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));
    accounts.add(account("governance", GovernanceIxBuilder.governancePda(programId), false, false));

    Map<String, Object> body =
        base("grant_studio_role", programId, rpcUrl, ownerWallet, disc, walletData(disc, adminWallet));
    body.put("adminWalletPubkey", adminWallet);
    body.put("studioRolePda", rolePda);
    body.put("accounts", accounts);
    body.put("note", "Principal signs grant_studio_role: the StudioRole PDA stores studio_admin.");
    return body;
  }

  /**
   * Builds {@code revoke_studio_role(wallet)}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param adminWallet wallet losing studio_admin
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildRevokeStudioRole(
      String programId, String rpcUrl, String ownerWallet, String adminWallet) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("revoke_studio_role");
    String rolePda = studioRolePda(programId, adminWallet);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("studioRole", rolePda, true, false));
    accounts.add(account("governance", GovernanceIxBuilder.governancePda(programId), false, false));

    Map<String, Object> body =
        base("revoke_studio_role", programId, rpcUrl, ownerWallet, disc, walletData(disc, adminWallet));
    body.put("adminWalletPubkey", adminWallet);
    body.put("studioRolePda", rolePda);
    body.put("accounts", accounts);
    body.put("note", "Principal signs revoke_studio_role: the wallet loses studio_admin on chain.");
    return body;
  }

  /**
   * Builds {@code set_partner_share(wallet, share_bps, starts_at, ends_at)}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param partnerWallet partner whose studio share changes
   * @param shareBps share in basis points (0 removes the partner)
   * @param startsAt window start, unix seconds
   * @param endsAt window end exclusive, unix seconds; 0 for open ended
   * @return instruction payload for the React wallet adapter
   * @throws IllegalArgumentException when the share or window cannot be valid on chain
   */
  @Override
  public Map<String, Object> buildSetPartnerShare(
      String programId,
      String rpcUrl,
      String ownerWallet,
      String partnerWallet,
      int shareBps,
      long startsAt,
      long endsAt) {
    if (shareBps < 0 || shareBps > MAX_TOTAL_BPS) {
      throw new IllegalArgumentException("shareBps must be 0..10000");
    }
    if (shareBps > 0 && (startsAt < 0 || (endsAt != 0 && endsAt <= startsAt))) {
      throw new IllegalArgumentException("window must start at 0 or later and end after it (0 = open)");
    }
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("set_partner_share");
    ByteBuffer data = ByteBuffer.allocate(8 + 32 + 2 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.put(SolanaPda.decode(partnerWallet));
    data.putShort((short) shareBps);
    data.putLong(startsAt);
    data.putLong(endsAt);

    String sharePda = partnerSharePda(programId, partnerWallet);
    String bookPda = partnerBookPda(programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("partnerShare", sharePda, true, false));
    accounts.add(account("partnerBook", bookPda, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));
    accounts.add(account("governance", GovernanceIxBuilder.governancePda(programId), false, false));

    Map<String, Object> body =
        base("set_partner_share", programId, rpcUrl, ownerWallet, disc, data.array());
    body.put("partnerWalletPubkey", partnerWallet);
    body.put("shareBps", shareBps);
    body.put("startsAt", startsAt);
    body.put("endsAt", endsAt);
    body.put("partnerSharePda", sharePda);
    body.put("partnerBookPda", bookPda);
    body.put("accounts", accounts);
    body.put(
        "note",
        shareBps == 0
            ? "Principal signs set_partner_share with 0 bps: the partner leaves the book."
            : "Principal signs set_partner_share: overlapping shares cannot exceed 10000 bps.");
    return body;
  }

  private static byte[] walletData(byte[] disc, String wallet) {
    ByteBuffer data = ByteBuffer.allocate(8 + 32);
    data.put(disc);
    data.put(SolanaPda.decode(wallet));
    return data.array();
  }

  private static Map<String, Object> base(
      String instruction,
      String programId,
      String rpcUrl,
      String ownerWallet,
      byte[] disc,
      byte[] data) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", instruction);
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("ownerWalletPubkey", ownerWallet);
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