package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IOwnerDigestRepository;
import com.eh8s.eh8s.service.claude.ClaudeClient;
import com.eh8s.eh8s.service.interfaces.ISlackBridgeService;
import com.eh8s.eh8s.service.interfaces.ITreasuryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OwnerDigestServiceTest {

  private static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";

  private final List<OwnerDigest> stored = new ArrayList<>();
  private final List<String> prompts = new ArrayList<>();
  private final List<Map<String, Object>> slackCalls = new ArrayList<>();
  private boolean metricsCollected;
  private boolean treasuryFails;

  private final IOwnerDigestRepository repo =
      new IOwnerDigestRepository() {
        @Override
        public Optional<ChainConfig> findActiveChainConfig() {
          ChainConfig cfg = new ChainConfig();
          cfg.setOwnerWalletPubkey(OWNER);
          return Optional.of(cfg);
        }

        @Override
        public int countAcademySubscriptions() {
          metricsCollected = true;
          return 2;
        }

        @Override
        public int countAcademyPaidSince(LocalDateTime since) {
          return 1;
        }

        @Override
        public int countAcademyConfirmed() {
          return 2;
        }

        @Override
        public int countSettlements() {
          return 3;
        }

        @Override
        public int countSettlementsConfirmed() {
          return 1;
        }

        @Override
        public List<String> findClaimStatuses() {
          return List.of("claimed", "pending");
        }

        @Override
        public int countClaims(String status) {
          return "pending".equals(status) ? 4 : 1;
        }

        @Override
        public int countActiveBandVaults() {
          return 1;
        }

        @Override
        public int countAgentEventsSince(LocalDateTime since) {
          return 7;
        }

        @Override
        public OwnerDigest insert(OwnerDigest digest) {
          digest.setId((long) stored.size() + 1);
          stored.add(digest);
          return digest;
        }

        @Override
        public Optional<OwnerDigest> latest() {
          return stored.isEmpty() ? Optional.empty() : Optional.of(stored.get(stored.size() - 1));
        }
      };

  private final ITreasuryService treasury =
      new ITreasuryService() {
        @Override
        public Map<String, Object> treasury(String walletPubkey) {
          if (treasuryFails) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY);
          }
          return Map.of("balanceUsdc", new BigDecimal("12.500000"));
        }

        @Override
        public Map<String, Object> buildWithdraw(String walletPubkey, BigDecimal amountUsdc) {
          throw new UnsupportedOperationException();
        }

        @Override
        public TreasuryWithdrawal confirmWithdraw(
            String walletPubkey, BigDecimal amountUsdc, String txSignature) {
          throw new UnsupportedOperationException();
        }
      };

  private final ISlackBridgeService slack =
      new ISlackBridgeService() {
        @Override
        public Map<String, Object> status(String walletPubkey) {
          return Map.of();
        }

        @Override
        public List<SlackDeliveryLog> deliveries(String walletPubkey) {
          return List.of();
        }

        @Override
        public SlackDeliveryLog notify(
            String walletPubkey, String text, Long ownerDecisionId, Long agentEventId) {
          slackCalls.add(Map.of("walletPubkey", walletPubkey, "text", text));
          SlackDeliveryLog log = new SlackDeliveryLog();
          log.setStatus("skipped");
          return log;
        }

        @Override
        public SlackDeliveryLog notifyDecision(
            com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision decision) {
          return null;
        }
      };

  private OwnerDigestService service(String key) {
    ClaudeClient claude =
        new ClaudeClient(key, "claude-sonnet-4-5", "http://127.0.0.1:1") {
          @Override
          public Map<String, Object> complete(String system, String user, int maxTokens) {
            prompts.add(user);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("text", "- 1 academy payment in the last 24 h.");
            out.put("model", "claude-sonnet-4-5");
            out.put("inputTokens", 200);
            out.put("outputTokens", 30);
            return out;
          }
        };
    return new OwnerDigestService(repo, claude, treasury, slack);
  }

  @Test
  void missingKeyIs503BeforeAnyWork() {
    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> service("").run(OWNER));
    assertEquals(503, e.getStatusCode().value());
    assertEquals("AI digest unavailable: ANTHROPIC_API_KEY not set", e.getReason());
    assertFalse(metricsCollected);
    assertTrue(stored.isEmpty());
  }

  @Test
  void ownerOnly() {
    OwnerDigestService svc = service("k");
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> svc.latest("YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU"))
            .getStatusCode()
            .value());
    assertEquals(
        400,
        assertThrows(ResponseStatusException.class, () -> svc.run(null))
            .getStatusCode()
            .value());
  }

  @Test
  void runSendsMetricsPromptStoresDigestAndPostsToSlack() {
    OwnerDigest row = service("k").run(OWNER);

    String prompt = prompts.get(0);
    assertTrue(prompt.startsWith("EH8S platform metrics for the last 24 hours (JSON):"));
    assertTrue(prompt.contains("\"paidLast24h\":1"));
    assertTrue(prompt.contains("\"treasuryBalanceUsdc\":12.500000"));
    assertTrue(prompt.endsWith("Write today's owner digest."));
    assertEquals(OWNER, row.getOwnerWalletPubkey());
    assertEquals("- 1 academy payment in the last 24 h.", row.getBody());
    assertEquals(200, row.getInputTokens());
    assertEquals(30, row.getOutputTokens());
    assertEquals("skipped", row.getSlackStatus());
    assertTrue(row.getMetricsJson().contains("\"pendingClaimsByStatus\":{\"claimed\":1,\"pending\":4}"));
    assertTrue(
        row.getMetricsJson()
            .contains("\"academySubscriptions\":{\"total\":2,\"paidLast24h\":1,\"onChainConfirmed\":2}"));
    assertTrue(row.getMetricsJson().contains("\"concertSettlements\":{\"total\":3,\"onChainSettled\":1}"));
    assertTrue(row.getMetricsJson().contains("\"bandVaultsActive\":1,\"agentEventsLast24h\":7"));
    assertEquals(OWNER, slackCalls.get(0).get("walletPubkey"));
    assertTrue(String.valueOf(slackCalls.get(0).get("text")).startsWith("EH8S owner digest\n"));
  }

  @Test
  void latestReportsAiAvailabilityAndTreasuryOutageIsNull() {
    Map<String, Object> before = service("").latest(OWNER);
    assertEquals(false, before.get("aiConfigured"));
    assertNull(before.get("latest"));

    treasuryFails = true;
    OwnerDigest row = service("k").run(OWNER);
    assertTrue(row.getMetricsJson().contains("\"treasuryBalanceUsdc\":null"));
    Map<String, Object> after = service("k").latest(OWNER);
    assertEquals(true, after.get("aiConfigured"));
    assertEquals(row, after.get("latest"));
  }
}
