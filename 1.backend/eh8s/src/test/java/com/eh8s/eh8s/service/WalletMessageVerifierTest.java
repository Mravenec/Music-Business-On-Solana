package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.solana.SolanaPda;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.interfaces.EdECPublicKey;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login signatures match a wallet key and fail when the message changes.
 */
class WalletMessageVerifierTest {

  @Test
  void acceptsTheSignatureOfTheLoginMessage() throws Exception {
    KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String wallet = SolanaPda.encode(solanaPublicKey((EdECPublicKey) pair.getPublic()));
    byte[] message = WalletMessageVerifier.message(wallet, "nonce-1");
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(pair.getPrivate());
    signer.update(message);
    String signature = SolanaPda.encode(signer.sign());

    assertTrue(WalletMessageVerifier.verify(wallet, message, signature));
  }

  @Test
  void rejectsASignatureOfADifferentNonce() throws Exception {
    KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String wallet = SolanaPda.encode(solanaPublicKey((EdECPublicKey) pair.getPublic()));
    byte[] signed = WalletMessageVerifier.message(wallet, "nonce-1");
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(pair.getPrivate());
    signer.update(signed);
    String signature = SolanaPda.encode(signer.sign());

    assertFalse(
        WalletMessageVerifier.verify(
            wallet, WalletMessageVerifier.message(wallet, "nonce-2"), signature));
  }

  private static byte[] solanaPublicKey(EdECPublicKey key) {
    EdECPoint point = key.getPoint();
    byte[] big = point.getY().toByteArray();
    byte[] y = new byte[32];
    int src = Math.max(0, big.length - 32);
    int dest = 32 - (big.length - src);
    System.arraycopy(big, src, y, dest, big.length - src);
    reverse(y);
    if (point.isXOdd()) {
      y[31] = (byte) (y[31] | 0x80);
    }
    if (new BigInteger(1, y).signum() < 0) {
      y = Arrays.copyOf(y, 32);
    }
    return y;
  }

  private static void reverse(byte[] bytes) {
    for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
      byte tmp = bytes[i];
      bytes[i] = bytes[j];
      bytes[j] = tmp;
    }
  }
}
