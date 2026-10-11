package com.eh8s.eh8s.service.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;

/**
 * Verifies that a DevNet signature really is the EH8S instruction a record endpoint expects:
 * program id, exact instruction data (discriminator + args), signer, and account PDAs.
 */
public interface ISolanaTxVerifier {

  /**
   * Fetches the transaction over RPC and verifies it.
   *
   * @param rpcUrl Solana RPC URL
   * @param programId expected EH8S program id
   * @param txSignature base58 signature from the client
   * @param instruction Anchor instruction name (for error text)
   * @param expectedDataHex full instruction data the server rebuilt (discriminator + Borsh args)
   * @param expectedSigner wallet that must be a required signer
   * @param expectedAccounts expected base58 pubkey per instruction account index ({@code null} = not checked)
   * @throws org.springframework.web.server.ResponseStatusException 400 naming the failed check,
   *     502 when RPC fails
   */
  void verify(
      String rpcUrl,
      String programId,
      String txSignature,
      String instruction,
      String expectedDataHex,
      String expectedSigner,
      List<String> expectedAccounts);

  /**
   * Verifies an already fetched {@code getTransaction} result node (encoding json).
   *
   * @param tx RPC {@code result} node
   * @param programId expected EH8S program id
   * @param instruction Anchor instruction name (for error text)
   * @param expectedDataHex full instruction data (discriminator + Borsh args)
   * @param expectedSigner wallet that must be a required signer
   * @param expectedAccounts expected base58 pubkey per instruction account index ({@code null} = not checked)
   * @throws org.springframework.web.server.ResponseStatusException 400 naming the failed check
   */
  void verifyTransaction(
      JsonNode tx,
      String programId,
      String instruction,
      String expectedDataHex,
      String expectedSigner,
      List<String> expectedAccounts);

  /**
   * Reconcile check: the signature must resolve to a successful confirmed transaction. Does not
   * rebuild instruction data or invent USDC amounts — presence and {@code meta.err == null} only.
   * Default throws; production {@code SolanaTxVerifier} implements the RPC check.
   *
   * @param rpcUrl Solana RPC URL
   * @param txSignature base58 signature
   * @throws org.springframework.web.server.ResponseStatusException 400 when missing/failed, 502 when
   *     RPC fails
   */
  default void requireSuccessfulTransaction(String rpcUrl, String txSignature) {
    throw new UnsupportedOperationException("requireSuccessfulTransaction");
  }

  /**
   * Builds the positional account list for {@link #verify}: element {@code i} is the pubkey expected at
   * instruction account {@code i}.
   *
   * @param byIndex expected pubkeys in account order; {@code null} leaves that account unchecked
   * @return fixed-size list that keeps {@code null} entries
   */
  static List<String> accounts(String... byIndex) {
    return Arrays.asList(byIndex);
  }
}
