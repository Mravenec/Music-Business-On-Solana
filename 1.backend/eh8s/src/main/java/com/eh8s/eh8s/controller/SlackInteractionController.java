package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ISlackInteractionController;
import com.eh8s.eh8s.service.interfaces.ISlackInteractionService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slack Interactivity Request URL target: {@code POST /api/slack/interactions}.
 */
@RestController
public class SlackInteractionController implements ISlackInteractionController {

  private final ISlackInteractionService slackInteractionService;

  /**
   * Creates the controller.
   *
   * @param slackInteractionService signed click handling
   */
  public SlackInteractionController(ISlackInteractionService slackInteractionService) {
    this.slackInteractionService = slackInteractionService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/api/slack/interactions")
  public Map<String, Object> interact(
      @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
      @RequestHeader(value = "X-Slack-Signature", required = false) String signature,
      @RequestBody(required = false) String rawBody) {
    return slackInteractionService.handle(timestamp, signature, rawBody);
  }
}
