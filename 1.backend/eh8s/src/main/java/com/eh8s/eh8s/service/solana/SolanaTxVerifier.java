package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Decodes a DevNet transaction and checks it against what a record endpoint expects. One verifier
 * for every confirm endpoint.
 */
@Component
public class SolanaTxVerifier implements ISolanaTxVerifier {

  private final ISolanaRpcClient solanaRpcClient;

  /**
   * Creates the verifier.
   *
   * @param solanaRpcClient JSON-RPC transport
   */
  public SolanaTxVerifier(ISolanaRpcClient solanaRpcClient) {
    this.solanaRpcClient = solanaRpcClient;
  }

  /** {@inheritDoc} */
  @Override
  public void verify(
      String rpcUrl,
      String programId,
      String txSignature,
      String instruction,
      String expectedDataHex,
      String expectedSigner,
      List<String> expectedAccounts) {
    verifyTransaction(
        solanaRpcClient.getTransaction(rpcUrl, txSignature),
        programId,
        instruction,
        expectedDataHex,
        expectedSigner,
        expectedAccounts);
  }

  /** {@inheritDoc} */
  @Override
  public void verifyTransaction(
      JsonNode tx,
      String programId,
      String instruction,
      String expectedDataHex,
      String expectedSigner,
      List<String> expectedAccounts) {
    if (tx == null || tx.isNull() || tx.isMissingNode()) {
      throw reject("DevNet transaction not found or not confirmed yet");
    }
    if (!tx.path("meta").path("err").isNull()) {
      throw reject("DevNet transaction failed on-chain; nothing recorded");
    }
    JsonNode message = tx.path("transaction").path("message");
    List<String> keys = accountKeys(tx, message);
    int signers = message.path("header").path("numRequiredSignatures").asInt(0);
    if (!keys.subList(0, Math.min(signers, keys.size())).contains(expectedSigner)) {
      throw reject("Wallet " + expectedSigner + " did not sign this transaction");
    }

    byte[] expected = HexFormat.of().parseHex(expectedDataHex);
    byte[] discriminator = Arrays.copyOf(expected, 8);
    boolean sawProgram = false;
    String firstMismatch = null;
    for (JsonNode ix : message.path("instructions")) {
      if (!programId.equals(key(keys, ix.path("programIdIndex").asInt(-1)))) {
        continue;
      }
      sawProgram = true;
      byte[] data = SolanaPda.decode(ix.path("data").asText(""));
      if (data.length < 8 || !Arrays.equals(Arrays.copyOf(data, 8), discriminator)) {
        continue;
      }
      String mismatch = mismatch(ix, keys, data, expected, expectedAccounts, instruction);
      if (mismatch == null) {
        return;
      }
      if (firstMismatch == null) {
        firstMismatch = mismatch;
      }
    }
    if (!sawProgram) {
      throw reject("Transaction has no top-level EH8S program instruction");
    }
    if (firstMismatch == null) {
      throw reject("Transaction is not a " + instruction + " instruction");
    }
    throw reject(firstMismatch);
  }

  private static String mismatch(
      JsonNode ix,
      List<String> keys,
      byte[] data,
      byte[] expected,
      List<String> expectedAccounts,
      String instruction) {
    if (!Arrays.equals(data, expected)) {
      return instruction + " arguments do not match the record (amount, ids, or weights)";
    }
    JsonNode accounts = ix.path("accounts");
    for (int i = 0; i < expectedAccounts.size(); i++) {
      String want = expectedAccounts.get(i);
      if (want == null) {
        continue;
      }
      String actual = key(keys, accounts.path(i).asInt(-1));
      if (!want.equals(actual)) {
        return instruction + " account #" + i + " is " + actual + ", expected " + want;
      }
    }
    return null;
  }

  private static List<String> accountKeys(JsonNode tx, JsonNode message) {
    List<String> keys = new ArrayList<>();
    message.path("accountKeys").forEach(k -> keys.add(k.asText()));
    JsonNode loaded = tx.path("meta").path("loadedAddresses");
    loaded.path("writable").forEach(k -> keys.add(k.asText()));
    loaded.path("readonly").forEach(k -> keys.add(k.asText()));
    return keys;
  }

  private static String key(List<String> keys, int index) {
    return index >= 0 && index < keys.size() ? keys.get(index) : null;
  }

  private static ResponseStatusException reject(String reason) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
  }
}
