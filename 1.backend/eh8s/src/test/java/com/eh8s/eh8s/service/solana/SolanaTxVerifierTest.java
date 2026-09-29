package com.eh8s.eh8s.service.solana;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SolanaTxVerifierTest {

  static final String PROGRAM = SolanaPdaTest.PROGRAM;
  static final String WALLET = SolanaPdaTest.WALLET;
  static final String PROFILE = "5UkTso3BDQHpX7tQd8sPW73BHBum1Tqsdkd2EF6FT4Ht";
  static final String UPSERT_HEX =
      HexFormat.of().formatHex(SettleClaimIxBuilder.anchorDiscriminator("upsert_musician_profile"))
          + "0302";

  private final SolanaTxVerifier verifier = new SolanaTxVerifier(new SolanaRpcClient());

  @Test
  void acceptsRealDevnetUpsert() {
    assertDoesNotThrow(
        () ->
            verifier.verifyTransaction(
                fixture("upsert-musician-profile"),
                PROGRAM,
                "upsert_musician_profile",
                UPSERT_HEX,
                WALLET,
                ISolanaTxVerifier.accounts(WALLET, PROFILE)));
  }

  @Test
  void skipsUncheckedAccountSlots() {
    assertDoesNotThrow(
        () ->
            verifier.verifyTransaction(
                fixture("upsert-musician-profile"),
                PROGRAM,
                "upsert_musician_profile",
                UPSERT_HEX,
                WALLET,
                ISolanaTxVerifier.accounts(null, PROFILE)));
  }

  @Test
  void rejectsWrongInstruction() {
    String settleHex =
        HexFormat.of().formatHex(SettleClaimIxBuilder.anchorDiscriminator("settle_concert"));
    assertReason(
        fixture("upsert-musician-profile"),
        "settle_concert",
        settleHex,
        WALLET,
        List.of(),
        "not a settle_concert instruction");
  }

  @Test
  void rejectsForgedArguments() {
    assertReason(
        fixture("upsert-musician-profile"),
        "upsert_musician_profile",
        UPSERT_HEX.substring(0, 16) + "0303",
        WALLET,
        List.of(),
        "arguments do not match");
  }

  @Test
  void rejectsWrongSigner() {
    assertReason(
        fixture("upsert-musician-profile"),
        "upsert_musician_profile",
        UPSERT_HEX,
        PROFILE,
        List.of(),
        "did not sign");
  }

  @Test
  void rejectsWrongPda() {
    assertReason(
        fixture("upsert-musician-profile"),
        "upsert_musician_profile",
        UPSERT_HEX,
        WALLET,
        ISolanaTxVerifier.accounts(null, SolanaPda.configPda(PROGRAM)),
        "account #1 is " + PROFILE);
  }

  @Test
  void rejectsFailedTransaction() {
    JsonNode tx = fixture("upsert-musician-profile");
    ((ObjectNode) tx.path("meta")).putObject("err").put("InstructionError", "custom");
    assertReason(tx, "upsert_musician_profile", UPSERT_HEX, WALLET, List.of(), "failed on-chain");
  }

  @Test
  void rejectsTransactionWithoutProgramInstruction() {
    JsonNode tx = fixture("upsert-musician-profile");
    ArrayNode keys = (ArrayNode) tx.path("transaction").path("message").path("accountKeys");
    keys.set(3, keys.get(2));
    assertReason(
        tx, "upsert_musician_profile", UPSERT_HEX, WALLET, List.of(), "no top-level EH8S program");
  }

  @Test
  void rejectsMissingTransaction() {
    assertReason(
        null, "upsert_musician_profile", UPSERT_HEX, WALLET, List.of(), "not found or not confirmed");
  }

  private void assertReason(
      JsonNode tx,
      String instruction,
      String dataHex,
      String signer,
      List<String> accounts,
      String reasonPart) {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> verifier.verifyTransaction(tx, PROGRAM, instruction, dataHex, signer, accounts));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains(reasonPart), ex.getReason());
  }

  private static JsonNode fixture(String name) {
    try (InputStream in =
        SolanaTxVerifierTest.class.getResourceAsStream("/solana/" + name + ".json")) {
      return new ObjectMapper().readTree(in).path("result");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
