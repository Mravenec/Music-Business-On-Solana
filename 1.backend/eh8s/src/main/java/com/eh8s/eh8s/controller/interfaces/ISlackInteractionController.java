package com.eh8s.eh8s.controller.interfaces;

import java.util.Map;

/**
 * Public Slack Interactivity endpoint (outside JWT; authenticated by the Slack request signature).
 */
public interface ISlackInteractionController {

  /**
   * Receives an owner's Approve / Reject click from a Slack decision message.
   *
   * @param timestamp {@code X-Slack-Request-Timestamp}
   * @param signature {@code X-Slack-Signature}
   * @param rawBody raw form body as Slack sent it (signed bytes)
   * @return outcome map
   */
  Map<String, Object> interact(String timestamp, String signature, String rawBody);
}
