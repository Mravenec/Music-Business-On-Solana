package com.eh8s.eh8s.service.solana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudioPowerIxBuilderTest {

  static final String PROGRAM = SolanaPdaTest.PROGRAM;
  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String WALLET = SolanaPdaTest.WALLET;

  private final StudioPowerIxBuilder builder = new StudioPowerIxBuilder();

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> accounts(Map<String, Object> ix) {
    return (List<Map<String, Object>>) ix.get("accounts");
  }

  private static List<String> names(Map<String, Object> ix) {
    return accounts(ix).stream().map(a -> (String) a.get("name")).toList();
  }

  @Test
  void grantUsesAnchorAccountOrderAndWalletArgument() {
    Map<String, Object> ix = builder.buildGrantStudioRole(PROGRAM, "rpc", OWNER, WALLET);
    assertEquals(
        List.of("owner", "config", "studioRole", "systemProgram", "governance"), names(ix));
    assertEquals(StudioPowerIxBuilder.studioRolePda(PROGRAM, WALLET), ix.get("studioRolePda"));
    assertEquals(true, accounts(ix).get(0).get("isSigner"));
    assertEquals(true, accounts(ix).get(2).get("isWritable"));

    byte[] data = HexFormat.of().parseHex((String) ix.get("dataHex"));
    assertEquals(40, data.length);
    assertEquals(
        HexFormat.of().formatHex(SettleClaimIxBuilder.anchorDiscriminator("grant_studio_role")),
        HexFormat.of().formatHex(data, 0, 8));
    assertEquals(WALLET, SolanaPda.encode(java.util.Arrays.copyOfRange(data, 8, 40)));
  }

  @Test
  void revokeOmitsSystemProgram() {
    Map<String, Object> ix = builder.buildRevokeStudioRole(PROGRAM, "rpc", OWNER, WALLET);
    assertEquals(List.of("owner", "config", "studioRole", "governance"), names(ix));
    assertEquals("revoke_studio_role", ix.get("instruction"));
  }

  @Test
  void setPartnerShareEncodesBorshArgumentsLittleEndian() {
    Map<String, Object> ix =
        builder.buildSetPartnerShare(PROGRAM, "rpc", OWNER, WALLET, 2_500, 1_700_000_000L, 0L);
    assertEquals(
        List.of("owner", "config", "partnerShare", "partnerBook", "systemProgram", "governance"),
        names(ix));
    byte[] data = HexFormat.of().parseHex((String) ix.get("dataHex"));
    assertEquals(8 + 32 + 2 + 8 + 8, data.length);
    ByteBuffer buf = ByteBuffer.wrap(data, 40, 18).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(2_500, buf.getShort() & 0xffff);
    assertEquals(1_700_000_000L, buf.getLong());
    assertEquals(0L, buf.getLong());
    assertEquals(StudioPowerIxBuilder.partnerSharePda(PROGRAM, WALLET), ix.get("partnerSharePda"));
    assertEquals(StudioPowerIxBuilder.partnerBookPda(PROGRAM), ix.get("partnerBookPda"));
  }

  @Test
  void setPartnerShareRefusesWhatTheProgramWouldRefuse() {
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.buildSetPartnerShare(PROGRAM, "rpc", OWNER, WALLET, 10_001, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.buildSetPartnerShare(PROGRAM, "rpc", OWNER, WALLET, 100, -1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.buildSetPartnerShare(PROGRAM, "rpc", OWNER, WALLET, 100, 50, 50));
    // removing a partner ignores the window
    Map<String, Object> ix = builder.buildSetPartnerShare(PROGRAM, "rpc", OWNER, WALLET, 0, 0, 0);
    assertTrue(((String) ix.get("note")).contains("leaves the book"));
  }

  @Test
  void pdasUseTheProgramSeedsAndDiffer() {
    String role = StudioPowerIxBuilder.studioRolePda(PROGRAM, WALLET);
    String share = StudioPowerIxBuilder.partnerSharePda(PROGRAM, WALLET);
    assertNotEquals(role, share);
    assertNotEquals(role, StudioPowerIxBuilder.partnerBookPda(PROGRAM));
    assertEquals(role, SolanaPda.walletPda("studio_role", WALLET, PROGRAM));
  }
}