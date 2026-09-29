package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.IBandVaultIxBuilder;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds create_band / update_spp_weights instruction metadata for owner wallet signing.
 * BandVault PDA seeds: ["band", band_id u64 LE].
 */
@Component
public class BandVaultIxBuilder implements IBandVaultIxBuilder {

  /** Program limit on vault members. */
  public static final int MAX_MEMBERS = 8;

  private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
  private static final String ALPHABET =
      "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

  /**
   * Builds create_band(band_id, members).
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays rent)
   * @param bandId MariaDB band id (PDA seed)
   * @param memberWallets member wallet pubkeys in vault order
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildCreateBand(
      String programId, String rpcUrl, String ownerWallet, long bandId, List<String> memberWallets) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("create_band");
    ByteBuffer data =
        ByteBuffer.allocate(8 + 8 + 4 + 32 * memberWallets.size()).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putLong(bandId);
    data.putInt(memberWallets.size());
    for (String wallet : memberWallets) {
      data.put(decodePubkey(wallet));
    }
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, true, true, null));
    accounts.add(account("config", null, false, false, List.of("eh8s", "config")));
    accounts.add(account("bandVault", null, true, false, List.of("band", "u64le:" + bandId)));
    accounts.add(account("systemProgram", SYSTEM_PROGRAM, false, false, null));
    Map<String, Object> body = base("create_band", programId, rpcUrl, ownerWallet, bandId, disc);
    body.put("memberWallets", memberWallets);
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put("note", "Owner signs create_band: BandVault PDA with equal weights (dust to last).");
    return body;
  }

  /**
   * Builds update_spp_weights(weights_bps).
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param bandId MariaDB band id (PDA seed)
   * @param weightsBps weights in vault member order (sum 10000)
   * @return instruction payload for the React wallet adapter
   */
  @Override
  public Map<String, Object> buildUpdateWeights(
      String programId, String rpcUrl, String ownerWallet, long bandId, List<Integer> weightsBps) {
    byte[] disc = SettleClaimIxBuilder.anchorDiscriminator("update_spp_weights");
    ByteBuffer data =
        ByteBuffer.allocate(8 + 4 + 2 * weightsBps.size()).order(ByteOrder.LITTLE_ENDIAN);
    data.put(disc);
    data.putInt(weightsBps.size());
    for (Integer w : weightsBps) {
      data.putShort((short) (int) w);
    }
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(account("owner", ownerWallet, false, true, null));
    accounts.add(account("config", null, false, false, List.of("eh8s", "config")));
    accounts.add(account("bandVault", null, true, false, List.of("band", "u64le:" + bandId)));
    Map<String, Object> body =
        base("update_spp_weights", programId, rpcUrl, ownerWallet, bandId, disc);
    body.put("weightsBps", weightsBps);
    body.put("dataHex", HexFormat.of().formatHex(data.array()));
    body.put("accounts", accounts);
    body.put("note", "Owner signs update_spp_weights: one weight per member, sum 10000 bps.");
    return body;
  }

  /**
   * Equal weights summing to 10000; the last member takes the remainder (same as the program).
   *
   * @param n member count
   * @return weights in bps
   */
  public static List<Integer> equalWeights(int n) {
    List<Integer> out = new ArrayList<>();
    if (n <= 0) {
      return out;
    }
    int base = 10_000 / n;
    for (int i = 0; i < n; i++) {
      out.add(i == n - 1 ? 10_000 - base * (n - 1) : base);
    }
    return out;
  }

  /**
   * Decodes a base58 Solana public key into its 32 bytes.
   *
   * @param base58 pubkey text
   * @return 32 bytes
   */
  public static byte[] decodePubkey(String base58) {
    BigInteger value = BigInteger.ZERO;
    for (char c : base58.toCharArray()) {
      int digit = ALPHABET.indexOf(c);
      if (digit < 0) {
        throw new IllegalArgumentException("Invalid base58 pubkey: " + base58);
      }
      value = value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
    }
    byte[] raw = value.toByteArray();
    int leadingZeros = 0;
    while (leadingZeros < base58.length() && base58.charAt(leadingZeros) == '1') {
      leadingZeros++;
    }
    int start = raw.length > 1 && raw[0] == 0 ? 1 : 0;
    int len = value.signum() == 0 ? 0 : raw.length - start;
    if (leadingZeros + len != 32) {
      throw new IllegalArgumentException("Pubkey is not 32 bytes: " + base58);
    }
    byte[] out = new byte[32];
    if (len > 0) {
      System.arraycopy(raw, start, out, leadingZeros, len);
    }
    return out;
  }

  private static Map<String, Object> base(
      String instruction, String programId, String rpcUrl, String ownerWallet, long bandId,
      byte[] disc) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("instruction", instruction);
    body.put("programId", programId);
    body.put("rpcUrl", rpcUrl);
    body.put("ownerWalletPubkey", ownerWallet);
    body.put("bandId", bandId);
    body.put("discriminatorHex", HexFormat.of().formatHex(disc));
    return body;
  }

  private static Map<String, Object> account(
      String name, String pubkey, boolean writable, boolean signer, List<String> pdaSeeds) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("pubkey", pubkey);
    row.put("isWritable", writable);
    row.put("isSigner", signer);
    if (pdaSeeds != null) {
      row.put("pdaSeeds", pdaSeeds);
    }
    return row;
  }
}
