package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.ISubscribeAcademyIxBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds subscribe_academy(plan_type, months) instruction metadata for DevNet wallet signing
 * (program v0.9.0: the program prices plan x months and splits 85/15 USDC).
 */
@Component
public class SubscribeAcademyIxBuilder implements ISubscribeAcademyIxBuilder {

  /** PDA seed prefix of AcademySubscription since v0.9.0 ({@code ["academy_sub", payer]}). */
  public static final String SEED = "academy_sub";

  /** Months a single subscribe_academy call may buy (inclusive). */
  public static final int MAX_MONTHS = 12;

  private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
  private static final BigDecimal MICRO = new BigDecimal("1000000");
  private static final int EXPIRES_AT_OFFSET = 8 + 32 + 1 + 2 + 8 + 8;

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
  @Override
  public Map<String, Object> build(
      String programId,
      String usdcMint,
      String rpcUrl,
      String payerWallet,
      int planType,
      int months,
      BigDecimal amountUsdc,
      String instructorUsdcAta,
      String payerUsdcAta) {
    BigDecimal amount = amountUsdc.setScale(6, RoundingMode.HALF_UP);
    long atomic = amount.multiply(MICRO).longValueExact();
    long instructorShare = Math.floorDiv(atomic * 15L, 100L);
    long treasuryShare = atomic - instructorShare;

    byte[] discriminator = anchorDiscriminator("subscribe_academy");
    ByteBuffer data = ByteBuffer.allocate(8 + 1 + 1).order(ByteOrder.LITTLE_ENDIAN);
    data.put(discriminator);
    data.put((byte) planType);
    data.put((byte) months);

    String subscriptionPda = academyPda(payerWallet, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("payer", payerWallet, true, true));
    accounts.add(
        account(
            "config", null, false, false, List.of("eh8s", "config"), "PDA seeds: [b\"eh8s\", b\"config\"]"));
    accounts.add(
        account(
            "academySubscription",
            subscriptionPda,
            true,
            false,
            List.of(SEED, payerWallet),
            "PDA seeds: [b\"academy_sub\", payer]"));
    accounts.add(account("payerUsdc", payerUsdcAta, true, false));
    accounts.add(account("treasuryUsdc", treasuryUsdc(programId, usdcMint), true, false));
    accounts.add(account("instructorUsdc", instructorUsdcAta, true, false));
    accounts.add(account("tokenProgram", TOKEN_PROGRAM, false, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "subscribe_academy");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("usdcMint", usdcMint);
    body.put("planType", planType);
    body.put("months", months);
    body.put("amountUsdc", amount);
    body.put("amountAtomic", atomic);
    body.put("treasuryShareAtomic", treasuryShare);
    body.put("instructorShareAtomic", instructorShare);
    body.put("treasuryShareBps", 8500);
    body.put("instructorShareBps", 1500);
    body.put("academySubscriptionPda", subscriptionPda);
    body.put("discriminatorHex", HexFormat.of().formatHex(discriminator));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put(
        "pdaNote",
        "Derive academy_subscription with seeds [\"academy_sub\", payerPubkey] under programId.");
    body.put(
        "note",
        "The program prices plan x months on-chain; renewals extend expires_at. Backend confirms via RPC before marking paid.");
    return body;
  }

  /**
   * AcademySubscription PDA of a payer ({@code ["academy_sub", payer]}).
   *
   * @param payerWallet base58 payer
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String academyPda(String payerWallet, String programId) {
    return SolanaPda.walletPda(SEED, payerWallet, programId);
  }

  /**
   * Reads {@code expires_at} from raw AcademySubscription account data (Anchor layout: discriminator,
   * musician, plan_code, months_paid, amount_usdc, started_at, expires_at, active, bump).
   *
   * @param data raw account bytes
   * @return expiry in UTC, or {@code null} when the data is too short
   */
  public static LocalDateTime decodeExpiresAt(byte[] data) {
    if (data == null || data.length < EXPIRES_AT_OFFSET + 8) {
      return null;
    }
    long seconds =
        ByteBuffer.wrap(data, EXPIRES_AT_OFFSET, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
  }

  /**
   * Program treasury USDC ATA (authority = {@code ["treasury"]} PDA) that the config pins as the
   * fee destination since program v0.5.0.
   *
   * @param programId DevNet program id
   * @param usdcMint configured USDC mint
   * @return base58 ATA address
   */
  public static String treasuryUsdc(String programId, String usdcMint) {
    return SolanaPda.associatedTokenAddress(SolanaPda.treasuryPda(programId), usdcMint);
  }

  private static Map<String, Object> account(
      String name, String pubkey, boolean writable, boolean signer) {
    return account(name, pubkey, writable, signer, null, null);
  }

  private static Map<String, Object> account(
      String name,
      String pubkey,
      boolean writable,
      boolean signer,
      List<String> pdaSeeds,
      String note) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("pubkey", pubkey);
    row.put("isWritable", writable);
    row.put("isSigner", signer);
    if (pdaSeeds != null) {
      row.put("pdaSeeds", pdaSeeds);
    }
    if (note != null) {
      row.put("note", note);
    }
    return row;
  }

  /**
   * Computes the 8-byte Anchor instruction discriminator for {@code global:<name>}.
   *
   * @param ixName instruction name
   * @return first 8 bytes of sha256
   */
  public static byte[] anchorDiscriminator(String ixName) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(("global:" + ixName).getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[8];
      System.arraycopy(hash, 0, out, 0, 8);
      return out;
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to hash Anchor discriminator", ex);
    }
  }
}
