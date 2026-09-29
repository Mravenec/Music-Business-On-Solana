package com.eh8s.eh8s.service.solana;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * Solana program-derived addresses and base58 in Java, so the backend derives record PDAs itself
 * instead of trusting the client.
 */
public final class SolanaPda {

  /** SPL Token program id. */
  public static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";

  /** Solana system program id. */
  public static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";

  /** Associated Token Account program id. */
  public static final String ASSOCIATED_TOKEN_PROGRAM =
      "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL";

  private static final String ALPHABET =
      "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
  private static final BigInteger P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
  private static final BigInteger D =
      BigInteger.valueOf(-121665)
          .multiply(BigInteger.valueOf(121666).modInverse(P))
          .mod(P);
  private static final byte[] PDA_MARKER =
      "ProgramDerivedAddress".getBytes(StandardCharsets.US_ASCII);

  private SolanaPda() {}

  /**
   * Derives the canonical PDA (highest bump that is off the ed25519 curve), like
   * {@code Pubkey::find_program_address}.
   *
   * @param seeds seed byte arrays (each at most 32 bytes)
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String findProgramAddress(List<byte[]> seeds, String programId) {
    byte[] program = decode(programId);
    for (int bump = 255; bump >= 0; bump--) {
      byte[] hash = sha256(seeds, new byte[] {(byte) bump}, program, PDA_MARKER);
      if (!isOnCurve(hash)) {
        return encode(hash);
      }
    }
    throw new IllegalStateException("No viable PDA bump for the given seeds");
  }

  /**
   * Eh8sConfig PDA {@code ["eh8s", "config"]}.
   *
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String configPda(String programId) {
    return findProgramAddress(List.of(seed("eh8s"), seed("config")), programId);
  }

  /**
   * PDA seeded by a text prefix and a wallet ({@code [prefix, wallet]}), e.g. academy, geo_sub,
   * musician.
   *
   * @param prefix text seed
   * @param wallet base58 wallet
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String walletPda(String prefix, String wallet, String programId) {
    return findProgramAddress(List.of(seed(prefix), decode(wallet)), programId);
  }

  /**
   * Treasury authority PDA ({@code ["treasury"]}) that owns the program treasury USDC ATA.
   *
   * @param programId base58 program id
   * @return base58 PDA
   */
  public static String treasuryPda(String programId) {
    return findProgramAddress(List.of(seed("treasury")), programId);
  }

  /**
   * Associated token account of {@code owner} for {@code mint} (SPL Token program). Works for
   * PDA owners as well as wallets.
   *
   * @param owner base58 token account owner
   * @param mint base58 mint
   * @return base58 ATA address
   */
  public static String associatedTokenAddress(String owner, String mint) {
    return findProgramAddress(
        List.of(decode(owner), decode(TOKEN_PROGRAM), decode(mint)), ASSOCIATED_TOKEN_PROGRAM);
  }

  /**
   * UTF-8 bytes of a string seed.
   *
   * @param seed text seed
   * @return seed bytes
   */
  public static byte[] seed(String seed) {
    return seed.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Little-endian u64 seed ({@code id.to_le_bytes()}).
   *
   * @param value unsigned 64-bit value
   * @return 8 bytes
   */
  public static byte[] u64le(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
  }

  /**
   * True when 32 bytes decompress to an ed25519 point (same test as curve25519-dalek decompress:
   * y reduced mod p, x² = (y² − 1) / (d·y² + 1) must be a square).
   *
   * @param key 32-byte compressed point
   * @return true when on the curve
   */
  static boolean isOnCurve(byte[] key) {
    byte[] be = new byte[32];
    for (int i = 0; i < 32; i++) {
      be[i] = key[31 - i];
    }
    be[0] &= 0x7f;
    BigInteger y = new BigInteger(1, be).mod(P);
    BigInteger y2 = y.multiply(y).mod(P);
    BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
    BigInteger v = D.multiply(y2).add(BigInteger.ONE).mod(P);
    if (v.signum() == 0) {
      return false;
    }
    BigInteger x2 = u.multiply(v.modInverse(P)).mod(P);
    if (x2.signum() == 0) {
      return true;
    }
    return x2.modPow(P.subtract(BigInteger.ONE).shiftRight(1), P).equals(BigInteger.ONE);
  }

  /**
   * Base58 (Bitcoin alphabet) decode.
   *
   * @param base58 encoded text
   * @return raw bytes
   */
  public static byte[] decode(String base58) {
    BigInteger n = BigInteger.ZERO;
    for (char c : base58.toCharArray()) {
      int digit = ALPHABET.indexOf(c);
      if (digit < 0) {
        throw new IllegalArgumentException("Invalid base58 character: " + c);
      }
      n = n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
    }
    byte[] raw = n.toByteArray();
    int strip = raw.length > 1 && raw[0] == 0 ? 1 : 0;
    int zeros = 0;
    while (zeros < base58.length() && base58.charAt(zeros) == '1') {
      zeros++;
    }
    byte[] body = n.signum() == 0 ? new byte[0] : Arrays.copyOfRange(raw, strip, raw.length);
    byte[] out = new byte[zeros + body.length];
    System.arraycopy(body, 0, out, zeros, body.length);
    return out;
  }

  /**
   * Base58 (Bitcoin alphabet) encode.
   *
   * @param bytes raw bytes
   * @return encoded text
   */
  public static String encode(byte[] bytes) {
    BigInteger n = new BigInteger(1, bytes);
    StringBuilder sb = new StringBuilder();
    BigInteger base = BigInteger.valueOf(58);
    while (n.signum() > 0) {
      BigInteger[] qr = n.divideAndRemainder(base);
      sb.append(ALPHABET.charAt(qr[1].intValue()));
      n = qr[0];
    }
    for (int i = 0; i < bytes.length && bytes[i] == 0; i++) {
      sb.append('1');
    }
    return sb.reverse().toString();
  }

  private static byte[] sha256(List<byte[]> seeds, byte[]... tail) {
    try {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      for (byte[] s : seeds) {
        buf.write(s);
      }
      for (byte[] t : tail) {
        buf.write(t);
      }
      return MessageDigest.getInstance("SHA-256").digest(buf.toByteArray());
    } catch (NoSuchAlgorithmException | java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
