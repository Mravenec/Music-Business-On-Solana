package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * Slack interactivity: owner Approve / Reject button clicks on decision messages.
 */
public interface ISlackInteractionService {

  /**
   * Verifies the Slack request signature (v0 HMAC-SHA256 over {@code v0:timestamp:body} with
   * {@code SLACK_SIGNING_SECRET}, timestamp within 5 minutes), applies the clicked decision exactly
   * once with {@code answered_via = slack}, replaces the Slack message with the outcome through the
   * payload's {@code response_url}, and logs the interaction.
   *
   * @param timestamp {@code X-Slack-Request-Timestamp} header (epoch seconds)
   * @param signature {@code X-Slack-Signature} header ({@code v0=<hex>})
   * @param rawBody raw form body ({@code payload=<url-encoded JSON>}) exactly as received
   * @return outcome map: decisionId, outcome (approved / rejected / already_decided / unknown), text
   * @throws org.springframework.web.server.ResponseStatusException 401 for a missing, stale or bad
   *     signature; 503 when the signing secret is not configured; 400 for an unreadable payload
   */
  Map<String, Object> handle(String timestamp, String signature, String rawBody);
}
