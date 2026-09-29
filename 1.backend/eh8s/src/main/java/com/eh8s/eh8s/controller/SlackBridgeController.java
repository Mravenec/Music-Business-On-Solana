package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ISlackBridgeController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.service.interfaces.ISlackBridgeService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves owner-only Slack / der-dirigent bridge status and delivery log JSON.
 */
@RestController
@RequestMapping("/api")
public class SlackBridgeController implements ISlackBridgeController {

  private final ISlackBridgeService slackBridgeService;

  /**
   * Creates the controller.
   *
   * @param slackBridgeService bridge use cases
   */
  public SlackBridgeController(ISlackBridgeService slackBridgeService) {
    this.slackBridgeService = slackBridgeService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/slack/status")
  public Map<String, Object> status(
      @RequestParam(value = "walletPubkey", required = false) String walletPubkey) {
    return slackBridgeService.status(walletPubkey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/slack/deliveries")
  public List<SlackDeliveryLog> deliveries(
      @RequestParam(value = "walletPubkey", required = true) String walletPubkey) {
    return slackBridgeService.deliveries(walletPubkey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/slack/notify")
  public SlackDeliveryLog notify(@RequestBody Map<String, Object> body) {
    return slackBridgeService.notify(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "text"),
        HttpBody.longValue(body, "ownerDecisionId"),
        HttpBody.longValue(body, "agentEventId"));
  }
}
