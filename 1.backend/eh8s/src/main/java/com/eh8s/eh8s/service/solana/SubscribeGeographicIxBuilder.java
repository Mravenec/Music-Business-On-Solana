package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.ISubscribeGeographicIxBuilder;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Builds subscribe_geographic(geo_code, tier, months) instruction metadata for DevNet wallet
 * signing (program v0.9.0: one GeographicSubscription per payer and zone, 100% to treasury).
 */
@Component
public class SubscribeGeographicIxBuilder implements ISubscribeGeographicIxBuilder {

  /** PDA seed prefix of GeographicSubscription ({@code ["geo_sub", payer, geo_code]}). */
  public static final String SEED = "geo_sub";

  /** Zone code accepted by the program: 2..24 of A-Z 0-9 _, the first two uppercase letters. */
  public static final Pattern GEO_CODE = Pattern.compile("[A-Z]{2}[A-Z0-9_]{0,22}");

  private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
  private static final BigDecimal MICRO = new BigDecimal("1000000");

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
  @Override
  public Map<String, Object> build(
      String programId,
      String usdcMint,
      String rpcUrl,
      String payerWallet,
      String geoCode,
      int tier,
      int months,
      BigDecimal amountUsdc,
      String payerUsdcAta) {
    BigDecimal amount = amountUsdc.setScale(6, RoundingMode.HALF_UP);
    long atomic = amount.multiply(MICRO).longValueExact();
    byte[] code = geoCode.getBytes(StandardCharsets.UTF_8);

    byte[] discriminator = anchorDiscriminator("subscribe_geographic");
    ByteBuffer data =
        ByteBuffer.allocate(8 + 4 + code.length + 1 + 1).order(ByteOrder.LITTLE_ENDIAN);
    data.put(discriminator);
    data.putInt(code.length);
    data.put(code);
    data.put((byte) tier);
    data.put((byte) months);

    String subscriptionPda = geoPda(payerWallet, geoCode, programId);
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("payer", payerWallet, true, true));
    accounts.add(
        account(
            "config", null, false, false, List.of("eh8s", "config"), "PDA seeds: [b\"eh8s\", b\"config\"]"));
    accounts.add(
        account(
            "geoSubscription",
            subscriptionPda,
            true,
            false,
            List.of(SEED, payerWallet, geoCode),
            "PDA seeds: [b\"geo_sub\", payer, geo_code]"));
    accounts.add(account("payerUsdc", payerUsdcAta, true, false));
    accounts.add(account("treasuryUsdc", SubscribeAcademyIxBuilder.treasuryUsdc(programId, usdcMint), true, false));
    accounts.add(account("tokenProgram", TOKEN_PROGRAM, false, false));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "subscribe_geographic");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("usdcMint", usdcMint);
    body.put("geoCode", geoCode);
    body.put("tier", tier);
    body.put("months", months);
    body.put("amountUsdc", amount);
    body.put("amountAtomic", atomic);
    body.put("geographicSubscriptionPda", subscriptionPda);
    body.put("discriminatorHex", HexFormat.of().formatHex(discriminator));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put(
        "pdaNote",
        "Derive geographic_subscription with seeds [\"geo_sub\", payerPubkey, geoCode] under programId.");
    body.put(
        "note",
        "The program prices tier x months on-chain; renewals extend expires_at. Backend confirms via RPC before marking paid.");
    return body;
  }

  /**
   * GeographicSubscription PDA of a payer and zone ({@code ["geo_sub", payer, geo_code]}).
   *
   * @param payerWallet base58 payer
   * @param geoCode zone seed
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String geoPda(String payerWallet, String geoCode, String programId) {
    return SolanaPda.findProgramAddress(
        List.of(SolanaPda.seed(SEED), SolanaPda.decode(payerWallet), SolanaPda.seed(geoCode)),
        programId);
  }

  /**
   * Reads {@code expires_at} from raw GeographicSubscription account data (Anchor layout:
   * discriminator, authority, geo_code string, tier_code, months_paid, amount_usdc, started_at,
   * expires_at, active, bump).
   *
   * @param data raw account bytes
   * @return expiry in UTC, or {@code null} when the data is too short
   */
  public static LocalDateTime decodeExpiresAt(byte[] data) {
    if (data == null || data.length < 8 + 32 + 4) {
      return null;
    }
    int codeLen = ByteBuffer.wrap(data, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    int offset = 8 + 32 + 4 + codeLen + 1 + 2 + 8 + 8;
    if (codeLen < 0 || data.length < offset + 8) {
      return null;
    }
    long seconds = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
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
