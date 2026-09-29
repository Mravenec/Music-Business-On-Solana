package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.ISlackBridgeRepository;
import com.eh8s.eh8s.service.interfaces.ISlackBridgeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner-only Slack / der-dirigent bridge. Non-owner wallets cannot notify or list deliveries.
 * Owner decision messages carry Block Kit Approve / Reject buttons answered through
 * {@code POST /api/slack/interactions}. Secrets stay in env; unset tokens skip gracefully for
 * local smoke.
 */
@Service
public class SlackBridgeService implements ISlackBridgeService {

  /** Block Kit action id of the Approve button (value = owner decision id). */
  public static final String APPROVE_ACTION = "owner_decision_approve";

  /** Block Kit action id of the Reject button (value = owner decision id). */
  public static final String REJECT_ACTION = "owner_decision_reject";

  /** Public path Slack's Interactivity Request URL must point at. */
  public static final String INTERACTIONS_PATH = "/api/slack/interactions";

  private final ISlackBridgeRepository slackBridgeRepository;
  private final IChainConfigRepository chainConfigRepository;
  private final String webhookUrl;
  private final String botToken;
  private final String channelId;
  private final String channelHint;
  private final boolean signingSecretSet;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /**
   * Creates the service.
   *
   * @param slackBridgeRepository delivery persistence
   * @param chainConfigRepository active chain_config (owner wallet)
   * @param webhookUrl optional Incoming Webhook URL (env)
   * @param botToken optional Slack bot token for dirigent chat.postMessage (env)
   * @param channelId optional Slack channel ID for bot posts (env)
   * @param channelHint display hint for owner UI
   * @param signingSecret Slack signing secret (env); only its presence is reported
   */
  public SlackBridgeService(
      ISlackBridgeRepository slackBridgeRepository,
      IChainConfigRepository chainConfigRepository,
      @Value("${eh8s.slack.webhook-url:}") String webhookUrl,
      @Value("${eh8s.slack.bot-token:}") String botToken,
      @Value("${eh8s.slack.channel-id:}") String channelId,
      @Value("${eh8s.slack.channel-hint:#eh8s-owner}") String channelHint,
      @Value("${eh8s.slack.signing-secret:}") String signingSecret) {
    this.slackBridgeRepository = slackBridgeRepository;
    this.chainConfigRepository = chainConfigRepository;
    this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    this.botToken = botToken == null ? "" : botToken.trim();
    this.channelId = channelId == null ? "" : channelId.trim();
    this.channelHint = channelHint;
    this.signingSecretSet = signingSecret != null && !signingSecret.isBlank();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> status(String walletPubkey) {
    String owner = requireOwnerPubkey();
    boolean authorized = isOwnerWallet(walletPubkey, owner);
    String mode = resolveMode();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ownerOnly", true);
    out.put("authorized", authorized);
    out.put("ownerWalletPubkey", owner);
    out.put("configured", !"skip".equals(mode));
    out.put("mode", mode);
    out.put("channelHint", channelHint);
    out.put("interactive", signingSecretSet);
    out.put("interactionsPath", INTERACTIONS_PATH);
    out.put(
        "interactiveNote",
        signingSecretSet
            ? "Approve / Reject buttons are live: Slack posts signed clicks to " + INTERACTIONS_PATH + "."
            : "SLACK_SIGNING_SECRET not set: Slack button clicks are refused (answer in the web inbox).");
    out.put(
        "note",
        !authorized
            ? "Platform owner wallet required — non-owner sessions cannot use der-dirigent notify."
            : "skip".equals(mode)
                ? "No Slack secrets configured — deliveries log as skipped (local smoke OK)."
                : "dirigent".equals(mode)
                    ? "Der-dirigent bot token configured — notify uses chat.postMessage."
                    : "Webhook configured — notify will POST text payloads.");
    return out;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SlackDeliveryLog> deliveries(String walletPubkey) {
    String owner = requireOwnerAndAuthorize(walletPubkey);
    return slackBridgeRepository.findByOwnerWallet(owner);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SlackDeliveryLog notify(
      String walletPubkey, String rawText, Long ownerDecisionId, Long agentEventId) {
    if (rawText == null || rawText.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
    }
    String wallet = walletPubkey == null || walletPubkey.isBlank() ? null : walletPubkey.trim();
    String owner = requireOwnerAndAuthorize(wallet);
    String text = rawText.trim();
    String preview = text.length() > 500 ? text.substring(0, 500) : text;

    List<Map<String, Object>> blocks =
        ownerDecisionId == null ? null : decisionBlocks(ownerDecisionId, text);
    return deliver(owner, wallet, ownerDecisionId, agentEventId, preview, text, blocks);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SlackDeliveryLog notifyDecision(OwnerDecision decision) {
    if (decision == null || decision.getId() == null) {
      return null;
    }
    try {
      String owner = requireOwnerPubkey();
      String text =
          "Owner decision #"
              + decision.getId()
              + " ("
              + decision.getSemaphore()
              + "): "
              + decision.getTitle();
      String detail = decision.getPayload() == null ? "" : decision.getPayload();
      if (detail.length() > 300) {
        detail = detail.substring(0, 300) + "...";
      }
      String blockText = detail.isBlank() || detail.equals(decision.getTitle()) ? text : text + "\n" + detail;
      String preview = text.length() > 500 ? text.substring(0, 500) : text;
      return deliver(
          owner, null, decision.getId(), null, preview, text, decisionBlocks(decision.getId(), blockText));
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /**
   * Block Kit body for one owner decision: the text plus Approve / Reject buttons whose value is the
   * decision id.
   *
   * @param decisionId owner decision primary key
   * @param text message text (mrkdwn)
   * @return Slack blocks
   */
  public static List<Map<String, Object>> decisionBlocks(Long decisionId, String text) {
    String value = String.valueOf(decisionId);
    Map<String, Object> section =
        Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text));
    Map<String, Object> approve =
        Map.of(
            "type", "button",
            "action_id", APPROVE_ACTION,
            "style", "primary",
            "value", value,
            "text", Map.of("type", "plain_text", "text", "Approve"));
    Map<String, Object> reject =
        Map.of(
            "type", "button",
            "action_id", REJECT_ACTION,
            "style", "danger",
            "value", value,
            "text", Map.of("type", "plain_text", "text", "Reject"));
    Map<String, Object> actions =
        Map.of("type", "actions", "block_id", "owner_decision_" + value, "elements", List.of(approve, reject));
    return List.of(section, actions);
  }

  private SlackDeliveryLog deliver(
      String owner,
      String requesterWallet,
      Long decisionId,
      Long eventId,
      String preview,
      String text,
      List<Map<String, Object>> blocks) {
    String mode = resolveMode();
    SlackDeliveryLog row = new SlackDeliveryLog();
    row.setOwnerDecisionId(decisionId);
    row.setAgentEventId(eventId);
    row.setOwnerWalletPubkey(owner);
    row.setRequesterWalletPubkey(requesterWallet);
    row.setBridgeMode(mode);
    row.setChannelHint(channelHint);
    row.setPayloadPreview(preview);
    row.setDeliveredAt(LocalDateTime.now());

    if ("skip".equals(mode)) {
      row.setStatus("skipped");
      row.setHttpStatus(null);
      row.setErrorMessage("Slack secrets not configured (webhook/bot token unset)");
      return slackBridgeRepository.insert(row);
    }

    Map<String, Object> message = new LinkedHashMap<>();
    if ("dirigent".equals(mode)) {
      message.put("channel", channelId);
    }
    message.put("text", text);
    if (blocks != null) {
      message.put("blocks", blocks);
    }
    if ("dirigent".equals(mode)) {
      return deliverViaDirigent(row, message);
    }
    return deliverViaWebhook(row, message);
  }

  private String toJson(Map<String, Object> message) {
    try {
      return mapper.writeValueAsString(message);
    } catch (JsonProcessingException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode Slack message");
    }
  }

  private SlackDeliveryLog deliverViaWebhook(SlackDeliveryLog row, Map<String, Object> message) {
    try {
      String json = toJson(message);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(webhookUrl))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      applyHttpOutcome(row, response.statusCode(), response.body());
    } catch (Exception ex) {
      applyException(row, ex);
    }
    return slackBridgeRepository.insert(row);
  }

  private SlackDeliveryLog deliverViaDirigent(SlackDeliveryLog row, Map<String, Object> message) {
    if (channelId.isBlank()) {
      row.setStatus("failed");
      row.setHttpStatus(null);
      row.setErrorMessage("eh8s.slack.channel-id / EH8S_SLACK_CHANNEL_ID required for dirigent mode");
      return slackBridgeRepository.insert(row);
    }
    try {
      String json = toJson(message);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://slack.com/api/chat.postMessage"))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json; charset=utf-8")
              .header("Authorization", "Bearer " + botToken)
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body() == null ? "" : response.body();
      boolean ok = response.statusCode() >= 200 && response.statusCode() < 300 && body.contains("\"ok\":true");
      row.setHttpStatus(response.statusCode());
      if (ok) {
        row.setStatus("delivered");
        row.setErrorMessage(null);
      } else {
        row.setStatus("failed");
        String err = body.isBlank() ? "chat.postMessage failed" : body;
        row.setErrorMessage(err.length() > 500 ? err.substring(0, 500) : err);
      }
    } catch (Exception ex) {
      applyException(row, ex);
    }
    return slackBridgeRepository.insert(row);
  }

  private String resolveMode() {
    if (!botToken.isBlank()) {
      return "dirigent";
    }
    if (!webhookUrl.isBlank()) {
      return "webhook";
    }
    return "skip";
  }

  private String requireOwnerPubkey() {
    return chainConfigRepository
        .findActive()
        .map(ChainConfig::getOwnerWalletPubkey)
        .filter(w -> w != null && !w.isBlank())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "chain_config.owner_wallet_pubkey missing"));
  }

  private String requireOwnerAndAuthorize(String walletPubkey) {
    String owner = requireOwnerPubkey();
    if (!isOwnerWallet(walletPubkey, owner)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the platform owner wallet may use the Slack dirigent bridge");
    }
    return owner;
  }

  private static boolean isOwnerWallet(String walletPubkey, String owner) {
    return walletPubkey != null
        && !walletPubkey.isBlank()
        && owner != null
        && owner.equals(walletPubkey.trim());
  }

  private static void applyHttpOutcome(SlackDeliveryLog row, int statusCode, String body) {
    row.setHttpStatus(statusCode);
    if (statusCode >= 200 && statusCode < 300) {
      row.setStatus("delivered");
      row.setErrorMessage(null);
    } else {
      row.setStatus("failed");
      String err = body == null ? "HTTP " + statusCode : body;
      row.setErrorMessage(err.length() > 500 ? err.substring(0, 500) : err);
    }
  }

  private static void applyException(SlackDeliveryLog row, Exception ex) {
    row.setStatus("failed");
    row.setHttpStatus(null);
    String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    row.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
  }

}
