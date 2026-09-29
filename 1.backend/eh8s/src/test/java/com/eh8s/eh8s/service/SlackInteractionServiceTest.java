package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.repository.interfaces.ISlackBridgeRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signed Slack clicks: real v0 HMAC-SHA256 signatures (valid, stale, tampered) and exactly-once
 * decisions.
 */
class SlackInteractionServiceTest {

  private static final String SECRET = "test-signing-secret-not-real";
  private static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  private static final long NOW = 1_790_000_000L;
  private static final String RESPONSE_URL = "https://hooks.slack.com/actions/T1/1/abc";

  private OpsRepositoryFake ops;
  private List<SlackDeliveryLog> log;
  private List<String[]> posts;

  @BeforeEach
  void setUp() {
    ops = new OpsRepositoryFake();
    ops.addPending(1L, "New venue booking waiting for owner review");
    log = new ArrayList<>();
    posts = new ArrayList<>();
  }

  @Test
  void validApproveUpdatesOnceAndReplacesMessage() {
    SlackInteractionService service = service(SECRET);
    String body = body(SlackBridgeService.APPROVE_ACTION, "1", RESPONSE_URL);
    String ts = String.valueOf(NOW);

    Map<String, Object> first = service.handle(ts, SlackInteractionService.sign(SECRET, ts, body), body);
    assertEquals("approved", first.get("outcome"));
    OwnerDecision d = ops.decisions.get(1L);
    assertEquals("approved", d.getStatus());
    assertEquals("slack", d.getAnsweredVia());
    assertEquals("kevin (U123)", d.getAnsweredBy());
    assertEquals(1, posts.size());
    assertEquals(RESPONSE_URL, posts.get(0)[0]);
    assertTrue(posts.get(0)[1].contains("\"replace_original\":true"));
    assertTrue(posts.get(0)[1].contains("approved in Slack by kevin (U123)"));
    assertEquals("approved", log.get(0).getStatus());
    assertEquals("interaction", log.get(0).getBridgeMode());
    assertEquals(OWNER, log.get(0).getOwnerWalletPubkey());

    String reject = body(SlackBridgeService.REJECT_ACTION, "1", RESPONSE_URL);
    Map<String, Object> second =
        service.handle(ts, SlackInteractionService.sign(SECRET, ts, reject), reject);
    assertEquals("already_decided", second.get("outcome"));
    assertTrue(String.valueOf(second.get("text")).contains("was already decided: approved (answered in slack)"));
    assertEquals("approved", ops.decisions.get(1L).getStatus());
  }

  @Test
  void rejectRecordsReasonAndSlackSource() {
    SlackInteractionService service = service(SECRET);
    String body = body(SlackBridgeService.REJECT_ACTION, "1", RESPONSE_URL);
    String ts = String.valueOf(NOW - 20);
    Map<String, Object> out = service.handle(ts, SlackInteractionService.sign(SECRET, ts, body), body);
    assertEquals("rejected", out.get("outcome"));
    assertEquals("Rejected in Slack by kevin (U123)", ops.decisions.get(1L).getRejectReason());
    assertEquals("slack", ops.decisions.get(1L).getAnsweredVia());
  }

  @Test
  void staleTimestampIs401() {
    SlackInteractionService service = service(SECRET);
    String body = body(SlackBridgeService.APPROVE_ACTION, "1", RESPONSE_URL);
    String ts = String.valueOf(NOW - SlackInteractionService.MAX_SKEW_SECONDS - 1);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.handle(ts, SlackInteractionService.sign(SECRET, ts, body), body));
    assertEquals(401, ex.getStatusCode().value());
    assertEquals("pending", ops.decisions.get(1L).getStatus());
  }

  @Test
  void tamperedBodyOrWrongSecretIs401() {
    SlackInteractionService service = service(SECRET);
    String ts = String.valueOf(NOW);
    String signed = body(SlackBridgeService.APPROVE_ACTION, "1", RESPONSE_URL);
    String tampered = body(SlackBridgeService.REJECT_ACTION, "1", RESPONSE_URL);
    String sig = SlackInteractionService.sign(SECRET, ts, signed);
    assertEquals(
        401,
        assertThrows(ResponseStatusException.class, () -> service.handle(ts, sig, tampered))
            .getStatusCode()
            .value());
    String wrong = SlackInteractionService.sign("another-secret", ts, signed);
    assertEquals(
        401,
        assertThrows(ResponseStatusException.class, () -> service.handle(ts, wrong, signed))
            .getStatusCode()
            .value());
    assertEquals("pending", ops.decisions.get(1L).getStatus());
    assertTrue(posts.isEmpty());
  }

  @Test
  void missingHeadersAre401AndMissingSecretIs503() {
    String body = body(SlackBridgeService.APPROVE_ACTION, "1", RESPONSE_URL);
    assertEquals(
        401,
        assertThrows(ResponseStatusException.class, () -> service(SECRET).handle(null, null, body))
            .getStatusCode()
            .value());
    String ts = String.valueOf(NOW);
    String sig = SlackInteractionService.sign(SECRET, ts, body);
    assertEquals(
        503,
        assertThrows(ResponseStatusException.class, () -> service("").handle(ts, sig, body))
            .getStatusCode()
            .value());
  }

  @Test
  void nonSlackResponseUrlIsNeverCalled() {
    SlackInteractionService service = service(SECRET);
    String body = body(SlackBridgeService.APPROVE_ACTION, "1", "https://evil.example.com/hook");
    String ts = String.valueOf(NOW);
    Map<String, Object> out = service.handle(ts, SlackInteractionService.sign(SECRET, ts, body), body);
    assertEquals("approved", out.get("outcome"));
    assertTrue(posts.isEmpty());
    assertNull(log.get(0).getHttpStatus());
    assertTrue(log.get(0).getErrorMessage().contains("not replaced"));
  }

  private SlackInteractionService service(String secret) {
    ChainConfig cfg = new ChainConfig();
    cfg.setOwnerWalletPubkey(OWNER);
    IChainConfigRepository chain = () -> Optional.of(cfg);
    ISlackBridgeRepository slack =
        new ISlackBridgeRepository() {
          @Override
          public List<SlackDeliveryLog> findByOwnerWallet(String ownerWalletPubkey) {
            return log;
          }

          @Override
          public SlackDeliveryLog insert(SlackDeliveryLog row) {
            log.add(row);
            return row;
          }
        };
    Clock clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    return new SlackInteractionService(
        ops,
        slack,
        chain,
        secret,
        clock,
        (url, json) -> {
          posts.add(new String[] {url, json});
          return 200;
        });
  }

  private static String body(String actionId, String value, String responseUrl) {
    String payload =
        "{\"type\":\"block_actions\",\"user\":{\"id\":\"U123\",\"username\":\"kevin\"},"
            + "\"response_url\":\""
            + responseUrl
            + "\",\"actions\":[{\"action_id\":\""
            + actionId
            + "\",\"value\":\""
            + value
            + "\"}]}";
    return "payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);
  }
}
