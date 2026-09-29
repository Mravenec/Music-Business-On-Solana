package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.IOpsRepository;
import com.eh8s.eh8s.repository.interfaces.ISlackBridgeRepository;
import com.eh8s.eh8s.service.interfaces.ISlackInteractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Signed Slack button clicks for owner decisions. The signing secret stays in env and is never
 * logged or returned.
 */
@Service
public class SlackInteractionService implements ISlackInteractionService {

  /** Slack's replay window: requests older (or newer) than this are refused. */
  static final long MAX_SKEW_SECONDS = 300;

  /** Only Slack-hosted response URLs are called back (no arbitrary outbound requests). */
  static final String RESPONSE_URL_PREFIX = "https://hooks.slack.com/";

  /** Posts a JSON body to a Slack response_url and returns the HTTP status. */
  interface ResponsePoster {
    int post(String url, String json) throws Exception;
  }

  private final IOpsRepository opsRepository;
  private final ISlackBridgeRepository slackBridgeRepository;
  private final IChainConfigRepository chainConfigRepository;
  private final String signingSecret;
  private final Clock clock;
  private final ResponsePoster poster;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the service with the system clock and a real HTTP poster.
   *
   * @param opsRepository owner decisions
   * @param slackBridgeRepository Slack delivery / interaction log
   * @param chainConfigRepository active chain_config (owner wallet for the log row)
   * @param signingSecret {@code SLACK_SIGNING_SECRET} (env)
   */
  @Autowired
  public SlackInteractionService(
      IOpsRepository opsRepository,
      ISlackBridgeRepository slackBridgeRepository,
      IChainConfigRepository chainConfigRepository,
      @Value("${eh8s.slack.signing-secret:}") String signingSecret) {
    this(
        opsRepository,
        slackBridgeRepository,
        chainConfigRepository,
        signingSecret,
        Clock.systemUTC(),
        httpPoster());
  }

  SlackInteractionService(
      IOpsRepository opsRepository,
      ISlackBridgeRepository slackBridgeRepository,
      IChainConfigRepository chainConfigRepository,
      String signingSecret,
      Clock clock,
      ResponsePoster poster) {
    this.opsRepository = opsRepository;
    this.slackBridgeRepository = slackBridgeRepository;
    this.chainConfigRepository = chainConfigRepository;
    this.signingSecret = signingSecret == null ? "" : signingSecret.trim();
    this.clock = clock;
    this.poster = poster;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> handle(String timestamp, String signature, String rawBody) {
    verify(timestamp, signature, rawBody);
    JsonNode payload = parsePayload(rawBody);
    String type = payload.path("type").asText("");
    if (!"block_actions".equals(type)) {
      Map<String, Object> ignored = new LinkedHashMap<>();
      ignored.put("outcome", "ignored");
      ignored.put("type", type);
      return ignored;
    }
    JsonNode action = payload.path("actions").path(0);
    String actionId = action.path("action_id").asText("");
    boolean approve = SlackBridgeService.APPROVE_ACTION.equals(actionId);
    if (!approve && !SlackBridgeService.REJECT_ACTION.equals(actionId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown Slack action");
    }
    Long decisionId;
    try {
      decisionId = Long.parseLong(action.path("value").asText("").trim());
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack action value is not a decision id");
    }
    String user = slackUser(payload.path("user"));

    String outcome;
    String text;
    Optional<OwnerDecision> current = opsRepository.findDecision(decisionId);
    if (current.isEmpty()) {
      outcome = "unknown";
      text = "Owner decision #" + decisionId + " no longer exists.";
    } else {
      Optional<OwnerDecision> updated =
          approve
              ? opsRepository.approve(decisionId, "slack", user)
              : opsRepository.reject(decisionId, "Rejected in Slack by " + user, "slack", user);
      if (updated.isPresent()) {
        OwnerDecision d = updated.get();
        outcome = d.getStatus();
        text =
            (approve ? ":white_check_mark: " : ":x: ")
                + "Owner decision #"
                + decisionId
                + " \""
                + d.getTitle()
                + "\" "
                + d.getStatus()
                + " in Slack by "
                + user
                + ".";
      } else {
        OwnerDecision d = opsRepository.findDecision(decisionId).orElse(current.get());
        outcome = "already_decided";
        text =
            "Owner decision #"
                + decisionId
                + " \""
                + d.getTitle()
                + "\" was already decided: "
                + d.getStatus()
                + (d.getAnsweredVia() == null ? "" : " (answered in " + d.getAnsweredVia() + ")")
                + ".";
      }
    }

    SlackDeliveryLog log = new SlackDeliveryLog();
    log.setOwnerDecisionId(current.isPresent() ? decisionId : null);
    log.setBridgeMode("interaction");
    log.setPayloadPreview(text.length() > 500 ? text.substring(0, 500) : text);
    log.setStatus(outcome);
    log.setDeliveredAt(LocalDateTime.now(clock));
    replaceMessage(payload.path("response_url").asText(""), text, log);
    chainConfigRepository
        .findActive()
        .map(ChainConfig::getOwnerWalletPubkey)
        .filter(w -> w != null && !w.isBlank())
        .ifPresent(
            owner -> {
              log.setOwnerWalletPubkey(owner);
              slackBridgeRepository.insert(log);
            });

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("decisionId", decisionId);
    out.put("outcome", outcome);
    out.put("text", text);
    out.put("answeredBy", user);
    out.put("responseUrlStatus", log.getHttpStatus());
    return out;
  }

  /**
   * Computes Slack's v0 signature for a request.
   *
   * @param secret signing secret
   * @param timestamp epoch seconds as sent in {@code X-Slack-Request-Timestamp}
   * @param body raw request body
   * @return {@code v0=<lowercase hex HMAC-SHA256>}
   */
  static String sign(String secret, String timestamp, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(("v0:" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
      return "v0=" + HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("HmacSHA256 unavailable", ex);
    }
  }

  private void verify(String timestamp, String signature, String rawBody) {
    if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Slack signature headers");
    }
    if (signingSecret.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Slack interactivity unavailable: SLACK_SIGNING_SECRET not set");
    }
    long ts;
    try {
      ts = Long.parseLong(timestamp.trim());
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad Slack timestamp");
    }
    long now = clock.instant().getEpochSecond();
    if (Math.abs(now - ts) > MAX_SKEW_SECONDS) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Stale Slack request");
    }
    String expected = sign(signingSecret, timestamp.trim(), rawBody == null ? "" : rawBody);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad Slack signature");
    }
  }

  private JsonNode parsePayload(String rawBody) {
    if (rawBody != null) {
      for (String pair : rawBody.split("&")) {
        int eq = pair.indexOf('=');
        if (eq > 0 && "payload".equals(pair.substring(0, eq))) {
          try {
            return mapper.readTree(URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
          } catch (Exception ex) {
            break;
          }
        }
      }
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack payload is missing or unreadable");
  }

  private static String slackUser(JsonNode user) {
    String id = user.path("id").asText("");
    String name = user.path("username").asText(user.path("name").asText(""));
    if (name.isBlank()) {
      return id.isBlank() ? "Slack user" : id;
    }
    return id.isBlank() ? name : name + " (" + id + ")";
  }

  private void replaceMessage(String responseUrl, String text, SlackDeliveryLog log) {
    if (!responseUrl.startsWith(RESPONSE_URL_PREFIX)) {
      log.setErrorMessage("response_url missing or not on hooks.slack.com; message not replaced");
      return;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("replace_original", true);
    body.put("text", text);
    body.put(
        "blocks",
        List.of(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text))));
    try {
      int status = poster.post(responseUrl, mapper.writeValueAsString(body));
      log.setHttpStatus(status);
      if (status < 200 || status >= 300) {
        log.setErrorMessage("response_url answered HTTP " + status);
      }
    } catch (Exception ex) {
      String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      log.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
    }
  }

  private static ResponsePoster httpPoster() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    return (url, json) -> {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json; charset=utf-8")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    };
  }
}
