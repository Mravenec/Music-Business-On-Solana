package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for the owner-only Slack / der-dirigent bridge.
 */
public interface ISlackBridgeController {

  /**
   * @param walletPubkey optional connected wallet
   * @return status without secrets
   */
  Map<String, Object> status(String walletPubkey);

  /**
   * @param walletPubkey must be platform owner
   * @return delivery log
   */
  List<SlackDeliveryLog> deliveries(String walletPubkey);

  /**
   * Reads walletPubkey, text and optional ownerDecisionId / agentEventId from the command body and
   * passes them to the service as primitives.
   *
   * @param body walletPubkey + text plus optional ownerDecisionId / agentEventId
   * @return delivery row
   */
  SlackDeliveryLog notify(Map<String, Object> body);
}
