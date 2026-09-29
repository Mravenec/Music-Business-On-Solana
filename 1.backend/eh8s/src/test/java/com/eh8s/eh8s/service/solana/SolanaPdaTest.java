package com.eh8s.eh8s.service.solana;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolanaPdaTest {

  static final String PROGRAM = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
  static final String WALLET = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";

  @Test
  void configPdaMatchesDevnetAccount() {
    assertEquals("GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF", SolanaPda.configPda(PROGRAM));
  }

  @Test
  void musicianProfilePdaMatchesDevnetAccount() {
    assertEquals(
        "5UkTso3BDQHpX7tQd8sPW73BHBum1Tqsdkd2EF6FT4Ht",
        SolanaPda.walletPda("musician", WALLET, PROGRAM));
  }

  @Test
  void base58RoundTrip() {
    byte[] raw = SolanaPda.decode(WALLET);
    assertEquals(32, raw.length);
    assertEquals(WALLET, SolanaPda.encode(raw));
    assertArrayEquals(new byte[] {0, 0, 1}, SolanaPda.decode(SolanaPda.encode(new byte[] {0, 0, 1})));
  }

  @Test
  void walletKeysAreOnCurveAndPdasAreNot() {
    assertTrue(SolanaPda.isOnCurve(SolanaPda.decode(WALLET)));
    assertFalse(SolanaPda.isOnCurve(SolanaPda.decode(SolanaPda.configPda(PROGRAM))));
  }
}
