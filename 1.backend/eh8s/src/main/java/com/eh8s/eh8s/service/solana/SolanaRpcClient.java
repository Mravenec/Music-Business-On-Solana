package com.eh8s.eh8s.service.solana;

import com.eh8s.eh8s.service.interfaces.ISolanaRpcClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimal Solana JSON-RPC transport. Decoding and per-endpoint checks live in
 * {@link SolanaTxVerifier}.
 */
@Component
public class SolanaRpcClient implements ISolanaRpcClient {

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Fetches a confirmed transaction ({@code encoding=json}, legacy and v0 messages).
   *
   * @param rpcUrl Solana RPC URL
   * @param signature base58 transaction signature
   * @return the RPC {@code result} node (JSON null when the signature is unknown or unconfirmed)
   * @throws ResponseStatusException 502 when the RPC call fails
   */
  @Override
  public JsonNode getTransaction(String rpcUrl, String signature) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jsonrpc", "2.0");
    body.put("id", 1);
    body.put("method", "getTransaction");
    body.put(
        "params",
        List.of(
            signature,
            Map.of(
                "encoding", "json",
                "commitment", "confirmed",
                "maxSupportedTransactionVersion", 0)));
    try {
      return post(rpcUrl, body).path("result");
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "DevNet RPC verify failed: " + ex.getMessage());
    }
  }

  /**
   * Reads an SPL token account balance at {@code confirmed} commitment.
   *
   * @param rpcUrl Solana RPC URL
   * @param tokenAccount base58 token account address
   * @return atomic units (0 when the account does not exist yet)
   * @throws ResponseStatusException 502 when the RPC call fails
   */
  @Override
  public long getTokenAccountBalance(String rpcUrl, String tokenAccount) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jsonrpc", "2.0");
    body.put("id", 1);
    body.put("method", "getTokenAccountBalance");
    body.put("params", List.of(tokenAccount, Map.of("commitment", "confirmed")));
    try {
      return Long.parseLong(post(rpcUrl, body).path("result").path("value").path("amount").asText("0"));
    } catch (ResponseStatusException ex) {
      if (ex.getReason() != null && ex.getReason().contains("could not find account")) {
        return 0L;
      }
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "DevNet RPC balance failed: " + ex.getMessage());
    }
  }

  /**
   * Reads raw account data at {@code confirmed} commitment.
   *
   * @param rpcUrl Solana RPC URL
   * @param address base58 account address
   * @return account data bytes, or {@code null} when the account does not exist
   * @throws ResponseStatusException 502 when the RPC call fails
   */
  @Override
  public byte[] getAccountData(String rpcUrl, String address) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jsonrpc", "2.0");
    body.put("id", 1);
    body.put("method", "getAccountInfo");
    body.put("params", List.of(address, Map.of("encoding", "base64", "commitment", "confirmed")));
    try {
      JsonNode value = post(rpcUrl, body).path("result").path("value");
      if (value.isMissingNode() || value.isNull()) {
        return null;
      }
      return java.util.Base64.getDecoder().decode(value.path("data").path(0).asText(""));
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "DevNet RPC account read failed: " + ex.getMessage());
    }
  }

  private JsonNode post(String rpcUrl, Map<String, Object> body) throws Exception {
    String json = mapper.writeValueAsString(body);
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(rpcUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "RPC HTTP " + response.statusCode());
    }
    JsonNode root = mapper.readTree(response.body());
    if (root.has("error") && !root.get("error").isNull()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "RPC error: " + root.get("error").toString());
    }
    return root;
  }
}
