package com.eh8s.eh8s.service.claude;

import com.eh8s.eh8s.service.interfaces.IClaudeClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimal Anthropic Messages API client (one request, no streaming, no SDK). The key comes from
 * {@code ANTHROPIC_API_KEY} only and is never logged.
 */
@Component
public class ClaudeClient implements IClaudeClient {

  /** Anthropic API version header value. */
  public static final String ANTHROPIC_VERSION = "2023-06-01";

  private final String apiKey;
  private final String model;
  private final String baseUrl;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the client.
   *
   * @param apiKey Anthropic API key (blank = AI disabled)
   * @param model Claude model id
   * @param baseUrl API origin (overridable for local stubs)
   */
  public ClaudeClient(
      @Value("${eh8s.claude.api-key:}") String apiKey,
      @Value("${eh8s.claude.model:claude-sonnet-4-5}") String model,
      @Value("${eh8s.claude.base-url:https://api.anthropic.com}") String baseUrl) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model;
    this.baseUrl = baseUrl;
  }

  /**
   * Whether an API key is configured.
   *
   * @return true when {@code ANTHROPIC_API_KEY} is set
   */
  @Override
  public boolean isConfigured() {
    return !apiKey.isEmpty();
  }

  /**
   * Configured model id.
   *
   * @return model id sent with every request
   */
  @Override
  public String model() {
    return model;
  }

  /**
   * Sends one Messages API request and returns the text reply plus token usage.
   *
   * @param system system prompt
   * @param user user message
   * @param maxTokens reply token cap
   * @return map with {@code text}, {@code model}, {@code inputTokens}, {@code outputTokens}
   * @throws ResponseStatusException 503 without a key; 502 when the API fails or replies empty
   */
  @Override
  public Map<String, Object> complete(String system, String user, int maxTokens) {
    if (!isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI digest unavailable: ANTHROPIC_API_KEY not set");
    }
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", model);
    request.put("max_tokens", maxTokens);
    request.put("system", system);
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", user);
    request.put("messages", List.of(message));
    String raw;
    try {
      raw =
          RestClient.create(baseUrl)
              .post()
              .uri("/v1/messages")
              .header("x-api-key", apiKey)
              .header("anthropic-version", ANTHROPIC_VERSION)
              .contentType(MediaType.APPLICATION_JSON)
              .body(mapper.writeValueAsString(request))
              .retrieve()
              .body(String.class);
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Claude API returned HTTP " + e.getStatusCode().value());
    } catch (RestClientException | JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Claude API unreachable");
    }
    return parse(raw);
  }

  private Map<String, Object> parse(String raw) {
    JsonNode root;
    try {
      root = mapper.readTree(raw == null ? "" : raw);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Claude API returned invalid JSON");
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode block : root.path("content")) {
      if ("text".equals(block.path("type").asText())) {
        text.append(block.path("text").asText());
      }
    }
    if (text.toString().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Claude API returned no text");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("text", text.toString().trim());
    out.put("model", root.path("model").asText(model));
    JsonNode usage = root.path("usage");
    out.put("inputTokens", usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asInt() : null);
    out.put(
        "outputTokens", usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asInt() : null);
    return out;
  }
}
