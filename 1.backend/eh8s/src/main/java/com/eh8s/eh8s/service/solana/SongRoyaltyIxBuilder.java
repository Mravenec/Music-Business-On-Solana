package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.ISongRoyaltyIxBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the program v0.10.0 per-song royalty instructions: {@code create_royalty_pool},
 * {@code deposit_royalties} and {@code pay_sync_license}. Every account pubkey is derived here so
 * the React wallet adapter only signs.
 */
@Component
public class SongRoyaltyIxBuilder implements ISongRoyaltyIxBuilder {

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
  /** Most members a song pool holds (same as the program). */
  public static final int MAX_MEMBERS = 8;
  /** Sync licensing platform fee (20%). */
  public static final int SYNC_FEE_BPS = 2_000;

  /**
   * RoyaltyPool PDA {@code ["royalty", track_id LE]}.
   *
   * @param trackId MariaDB track id
   * @param programId program id
   * @return base58 PDA
   */
  public static String poolPda(long trackId, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("royalty"), SolanaPda.u64le(trackId)), programId);
  }

  /**
   * SyncLicense PDA {@code ["sync", track_id LE, deal_id LE]}.
   *
   * @param trackId MariaDB track id
   * @param dealId MariaDB sync_license_deal id
   * @param programId program id
   * @return base58 PDA
   */
  public static String syncPda(long trackId, long dealId, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed("sync"), SolanaPda.u64le(trackId), SolanaPda.u64le(dealId)),
        programId);
  }

  /**
   * Converts a 2-decimal USDC amount to 6-decimal atomic units.
   *
   * @param amountUsdc USDC amount
   * @return atomic units
   */
  public static long atomic(BigDecimal amountUsdc) {
    return amountUsdc.setScale(6, RoundingMode.DOWN).movePointRight(6).longValueExact();
  }

  /**
   * Builds {@code create_royalty_pool(track_id, members, splits_bps)}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param signerWallet owner or WAVE agent (signer, pays pool rent)
   * @param trackId MariaDB track id
   * @param memberWallets member wallets in pool order
   * @param splitsBps member splits in pool order (sum 10000)
   * @param agentAccount AgentAuthority PDA or program id (owner)
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildCreatePool(
      String programId,
      String rpcUrl,
      String signerWallet,
      long trackId,
      List<String> memberWallets,
      List<Integer> splitsBps,
      String agentAccount) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("create_royalty_pool");
    int n = memberWallets.size();
    ByteBuffer data =
        ByteBuffer.allocate(8 + 8 + 4 + 32 * n + 4 + 2 * n).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(trackId);
    data.putInt(n);
    for (String wallet : memberWallets) {
      data.put(SolanaPda.decode(wallet));
    }
    data.putInt(splitsBps.size());
    for (Integer bps : splitsBps) {
      data.putShort(bps.shortValue());
    }
    String pool = poolPda(trackId, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("authority", signerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("royaltyPool", pool, true, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));
    accounts.add(account("agentAuthority", agentAccount, false, false));

    Map<String, Object> body = payload("create_royalty_pool", programId, rpcUrl, disc, data.array());
    body.put("signerWalletPubkey", signerWallet);
    body.put("trackId", trackId);
    body.put("royaltyPoolPda", pool);
    body.put("agentAuthorityAccount", agentAccount);
    body.put("memberWallets", memberWallets);
    body.put("splitsBps", splitsBps);
    body.put("accounts", accounts);
    body.put(
        "note",
        "Owner or WAVE agent signs create_royalty_pool once per song: 1-8 members, splits sum"
            + " 10000 bps.");
    return body;
  }

  /**
   * Builds {@code deposit_royalties(track_id, amount)}. Remaining accounts are the member
   * MusicianProfile PDAs in pool order.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param depositorWallet owner or WAVE agent (signer, source of USDC)
   * @param trackId MariaDB track id
   * @param amountAtomic USDC amount (6 decimals)
   * @param agentAccount AgentAuthority PDA or program id (owner)
   * @param memberWallets pool members in pool order
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildDeposit(
      String programId,
      String rpcUrl,
      String usdcMint,
      String depositorWallet,
      long trackId,
      long amountAtomic,
      String agentAccount,
      List<String> memberWallets) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("deposit_royalties");
    ByteBuffer data = ByteBuffer.allocate(8 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(trackId);
    data.putLong(amountAtomic);
    String pool = poolPda(trackId, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("depositor", depositorWallet, false, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("royaltyPool", pool, true, false));
    accounts.add(
        account(
            "depositorUsdc",
            SolanaPda.associatedTokenAddress(depositorWallet, usdcMint),
            true,
            false));
    accounts.add(account("vaultUsdc", vaultUsdc(programId, usdcMint), true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));
    accounts.add(account("agentAuthority", agentAccount, false, false));
    List<String> profiles = addMemberProfiles(accounts, memberWallets, programId);

    Map<String, Object> body = payload("deposit_royalties", programId, rpcUrl, disc, data.array());
    body.put("signerWalletPubkey", depositorWallet);
    body.put("trackId", trackId);
    body.put("royaltyPoolPda", pool);
    body.put("agentAuthorityAccount", agentAccount);
    body.put("memberWallets", memberWallets);
    body.put("memberProfilePdas", profiles);
    body.put("amountAtomic", amountAtomic);
    body.put("accounts", accounts);
    body.put(
        "note",
        "Owner or WAVE agent signs deposit_royalties: USDC to the vault, pending credited per song"
            + " split.");
    return body;
  }

  /**
   * Builds {@code pay_sync_license(track_id, deal_id, amount)}: 20% to treasury, 80% credited to
   * the song pool members. Remaining accounts are the member MusicianProfile PDAs in pool order.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param payerWallet licensee wallet (signer, pays USDC and license rent)
   * @param trackId MariaDB track id
   * @param dealId MariaDB sync_license_deal id
   * @param amountAtomic gross license USDC (6 decimals)
   * @param memberWallets pool members in pool order
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildSyncPay(
      String programId,
      String rpcUrl,
      String usdcMint,
      String payerWallet,
      long trackId,
      long dealId,
      long amountAtomic,
      List<String> memberWallets) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("pay_sync_license");
    ByteBuffer data = ByteBuffer.allocate(8 + 8 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(trackId);
    data.putLong(dealId);
    data.putLong(amountAtomic);
    String pool = poolPda(trackId, programId);
    String license = syncPda(trackId, dealId, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("payer", payerWallet, true, true));
    accounts.add(account("config", SolanaPda.configPda(programId), false, false));
    accounts.add(account("royaltyPool", pool, true, false));
    accounts.add(account("syncLicense", license, true, false));
    accounts.add(
        account("payerUsdc", SolanaPda.associatedTokenAddress(payerWallet, usdcMint), true, false));
    accounts.add(
        account(
            "treasuryUsdc", SubscribeAcademyIxBuilder.treasuryUsdc(programId, usdcMint), true, false));
    accounts.add(account("vaultUsdc", vaultUsdc(programId, usdcMint), true, false));
    accounts.add(account("tokenProgram", SolanaPda.TOKEN_PROGRAM, false, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));
    List<String> profiles = addMemberProfiles(accounts, memberWallets, programId);

    long fee = amountAtomic * SYNC_FEE_BPS / 10_000L;
    Map<String, Object> body = payload("pay_sync_license", programId, rpcUrl, disc, data.array());
    body.put("signerWalletPubkey", payerWallet);
    body.put("trackId", trackId);
    body.put("dealId", dealId);
    body.put("royaltyPoolPda", pool);
    body.put("syncLicensePda", license);
    body.put("memberWallets", memberWallets);
    body.put("memberProfilePdas", profiles);
    body.put("amountAtomic", amountAtomic);
    body.put("eh8sFeeAtomic", fee);
    body.put("artistAtomic", amountAtomic - fee);
    body.put("accounts", accounts);
    body.put(
        "note",
        "Licensee signs pay_sync_license once per deal: 20% to the EH8S treasury, 80% credited to"
            + " the song members.");
    return body;
  }

  /**
   * Decodes a RoyaltyPool account: disc, track_id, members, splits_bps, total_usdc,
   * sync_total_usdc, bump.
   *
   * @param data raw account bytes, or {@code null}
   * @return decoded fields, or {@code null} when the account does not exist
   */
  public static Map<String, Object> decodePool(byte[] data) {
    if (data == null || data.length < 8 + 8 + 4) {
      return null;
    }
    ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    buf.position(8);
    long trackId = buf.getLong();
    int n = buf.getInt();
    List<String> members = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      byte[] key = new byte[32];
      buf.get(key);
      members.add(SolanaPda.encode(key));
    }
    int m = buf.getInt();
    List<Integer> splits = new ArrayList<>();
    for (int i = 0; i < m; i++) {
      splits.add(Short.toUnsignedInt(buf.getShort()));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("trackId", trackId);
    out.put("members", members);
    out.put("splitsBps", splits);
    out.put("totalAtomic", buf.getLong());
    out.put("syncTotalAtomic", buf.getLong());
    return out;
  }

  private static String vaultUsdc(String programId, String usdcMint) {
    return SolanaPda.associatedTokenAddress(
        SolanaPda.findProgramAddress(List.of(SolanaPda.seed("vault")), programId), usdcMint);
  }

  private static List<String> addMemberProfiles(
      List<Map<String, Object>> accounts, List<String> memberWallets, String programId) {
    List<String> profiles = new ArrayList<>();
    for (int i = 0; i < memberWallets.size(); i++) {
      String profile = SolanaPda.walletPda("musician", memberWallets.get(i), programId);
      profiles.add(profile);
      accounts.add(account("memberProfile" + i, profile, true, false));
    }
    return profiles;
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
