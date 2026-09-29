package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * Anthropic Messages API client (the key stays in the environment).
 */
public interface IClaudeClient {

  /**
   * Whether an API key is configured.
   *
   * @return true when {@code ANTHROPIC_API_KEY} is set
   */
  boolean isConfigured();

  /**
   * Configured model id.
   *
   * @return model id sent with every request
   */
  String model();

  /**
   * Sends one Messages API request and returns the text reply plus token usage.
   *
   * @param system system prompt
   * @param user user message
   * @param maxTokens reply token cap
   * @return map with {@code text}, {@code model}, {@code inputTokens}, {@code outputTokens}
   * @throws ResponseStatusException 503 without a key; 502 when the API fails or replies empty
   */
  Map<String, Object> complete(String system, String user, int maxTokens);
}
