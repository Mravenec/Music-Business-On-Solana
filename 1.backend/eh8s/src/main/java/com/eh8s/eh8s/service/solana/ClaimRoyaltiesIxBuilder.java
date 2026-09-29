package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IClaimRoyaltiesIxBuilder;
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
 * Builds claim_royalties instruction metadata for DevNet wallet signing (vault → musician USDC).
 */
@Component
public class ClaimRoyaltiesIxBuilder implements IClaimRoyaltiesIxBuilder {

  private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
  private static final BigDecimal MICRO = new BigDecimal("1000000");

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
  @Override
  public Map<String, Object> build(
      String programId,
      String usdcMint,
      String rpcUrl,
      String musicianWallet,
      BigDecimal amountUsdc,
      String vaultUsdcAta,
      String musicianUsdcAta) {
    BigDecimal amount = amountUsdc.setScale(6, RoundingMode.HALF_UP);
    long atomic = amount.multiply(MICRO).longValueExact();

    byte[] discriminator = SettleClaimIxBuilder.anchorDiscriminator("claim_royalties");
    ByteBuffer data = ByteBuffer.allocate(8 + 8).order(ByteOrder.LITTLE_ENDIAN);
    data.put(discriminator);
    data.putLong(atomic);

    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("musician", musicianWallet, false, true));
    accounts.add(
        account(
            "config", null, false, false, List.of("eh8s", "config"), "PDA seeds: [b\"eh8s\", b\"config\"]"));
    accounts.add(
        account(
            "musicianProfile",
            null,
            true,
            false,
            List.of("musician", musicianWallet),
            "PDA seeds: [b\"musician\", musician]"));
    accounts.add(
        account(
            "vaultAuthority",
            null,
            false,
            false,
            List.of("vault"),
            "PDA seeds: [b\"vault\"]"));
    accounts.add(account("vaultUsdc", vaultUsdcAta, true, false));
    accounts.add(account("musicianUsdc", musicianUsdcAta, true, false));
    accounts.add(account("tokenProgram", TOKEN_PROGRAM, false, false));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", "claim_royalties");
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("usdcMint", usdcMint);
    body.put("amountUsdc", amount);
    body.put("amountAtomic", atomic);
    body.put("discriminatorHex", HexFormat.of().formatHex(discriminator));
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put(
        "pdaNote",
        "Derive musician_profile with seeds [\"musician\", musicianPubkey]; vault authority with [\"vault\"].");
    body.put(
        "note",
        "Wallet must sign a real DevNet claim that transfers USDC. Backend confirms via RPC before marking claimed.");
    return body;
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
}
