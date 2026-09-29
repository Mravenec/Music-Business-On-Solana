package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.solana.SolanaPda;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;

/**
 * Checks a Solana wallet signature over the login message (Ed25519, RFC 8032).
 */
public final class WalletMessageVerifier {

  private WalletMessageVerifier() {}

  /**
   * Returns true when {@code signatureBase58} is the wallet's signature of {@code message}.
   *
   * @param walletBase58 Solana public key
   * @param message exact bytes the wallet signed
   * @param signatureBase58 64-byte signature
   * @return whether the signature matches
   */
  public static boolean verify(String walletBase58, byte[] message, String signatureBase58) {
    try {
      byte[] pub = SolanaPda.decode(walletBase58);
      byte[] sig = SolanaPda.decode(signatureBase58);
      if (pub.length != 32 || sig.length != 64 || message == null) {
        return false;
      }
      PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(publicSpec(pub));
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(key);
      verifier.update(message);
      return verifier.verify(sig);
    } catch (RuntimeException | java.security.GeneralSecurityException ex) {
      return false;
    }
  }

  /**
   * Builds the text Phantom must sign.
   *
   * @param walletBase58 wallet
   * @param nonce one-time server nonce
   * @return message bytes
   */
  public static byte[] message(String walletBase58, String nonce) {
    return ("Sign in to EH8S\nWallet: " + walletBase58 + "\nNonce: " + nonce)
        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static EdECPublicKeySpec publicSpec(byte[] solana) {
    byte[] encoded = Arrays.copyOf(solana, 32);
    int last = encoded[31] & 0xff;
    boolean xOdd = (last & 0x80) != 0;
    encoded[31] = (byte) (last & 0x7f);
    reverse(encoded);
    BigInteger y = new BigInteger(1, encoded);
    return new EdECPublicKeySpec(NamedParameterSpec.ED25519, new EdECPoint(xOdd, y));
  }

  private static void reverse(byte[] bytes) {
    for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
      byte tmp = bytes[i];
      bytes[i] = bytes[j];
      bytes[j] = tmp;
    }
  }
}
