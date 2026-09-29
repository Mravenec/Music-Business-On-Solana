package com.eh8s.eh8s.service.solana;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared Anchor helpers for the DevNet instruction builders.
 */
public final class SettleClaimIxBuilder {

  private SettleClaimIxBuilder() {}

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
