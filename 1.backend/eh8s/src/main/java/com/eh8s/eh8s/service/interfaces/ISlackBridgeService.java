package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import java.util.List;
import java.util.Map;

/**
 * Owner-only Slack / der-dirigent bridge (webhook or bot token). Decision messages carry Block Kit
 * Approve / Reject buttons.
 */
public interface ISlackBridgeService {

  /**
   * Returns bridge status without revealing secrets.
   *
   * @param walletPubkey connected wallet (may be null — then authorized=false)
   * @return status map
   */
  Map<String, Object> status(String walletPubkey);

  /**
   * Lists delivery rows for the platform owner wallet only.
   *
   * @param walletPubkey must match chain_config.owner_wallet_pubkey
   * @return delivery log newest first
   */
  List<SlackDeliveryLog> deliveries(String walletPubkey);

  /**
   * Attempts delivery (or skips when secrets unset) and logs the result. Rejects non-owner wallets.
   * With ownerDecisionId the message carries Approve / Reject buttons for that decision.
   *
   * @param walletPubkey caller wallet; must match chain_config.owner_wallet_pubkey (403 otherwise)
   * @param text message text (required; the log keeps the first 500 characters)
   * @param ownerDecisionId optional decision id; adds Approve / Reject buttons
   * @param agentEventId optional agent event id stored on the delivery row
   * @return delivery log row
   */
  SlackDeliveryLog notify(String walletPubkey, String text, Long ownerDecisionId, Long agentEventId);

  /**
   * Sends a freshly escalated owner decision to Slack with Approve / Reject buttons, on behalf of
   * the platform (no wallet). Never throws: a Slack failure must not fail the agent action.
   *
   * @param decision the pending decision just stored
   * @return delivery log row, or null when nothing could be logged
   */
  SlackDeliveryLog notifyDecision(OwnerDecision decision);
}
