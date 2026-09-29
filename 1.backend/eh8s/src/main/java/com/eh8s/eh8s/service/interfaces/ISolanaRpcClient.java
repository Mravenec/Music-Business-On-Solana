package com.eh8s.eh8s.service.interfaces;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DevNet JSON-RPC reads (transactions, token balances, account data).
 */
public interface ISolanaRpcClient {

  /**
   * Fetches a confirmed transaction ({@code encoding=json}, legacy and v0 messages).
   *
   * @param rpcUrl Solana RPC URL
   * @param signature base58 transaction signature
   * @return the RPC {@code result} node (JSON null when the signature is unknown or unconfirmed)
   * @throws ResponseStatusException 502 when the RPC call fails
   */
  JsonNode getTransaction(String rpcUrl, String signature);

  /**
   * Reads an SPL token account balance at {@code confirmed} commitment.
   *
   * @param rpcUrl Solana RPC URL
   * @param tokenAccount base58 token account address
   * @return atomic units (0 when the account does not exist yet)
   * @throws ResponseStatusException 502 when the RPC call fails
   */
  long getTokenAccountBalance(String rpcUrl, String tokenAccount);

  /**
   * Reads raw account data at {@code confirmed} commitment.
   *
   * @param rpcUrl Solana RPC URL
   * @param address base58 account address
   * @return account data bytes, or {@code null} when the account does not exist
   * @throws ResponseStatusException 502 when the RPC call fails
   */
  byte[] getAccountData(String rpcUrl, String address);
}
