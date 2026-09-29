package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IClaudeClient;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.repository.interfaces.IOwnerDigestRepository;
import com.eh8s.eh8s.service.claude.ClaudeClient;
import com.eh8s.eh8s.service.interfaces.IOwnerDigestService;
import com.eh8s.eh8s.service.interfaces.ISlackBridgeService;
import com.eh8s.eh8s.service.interfaces.ITreasuryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner digest: live MariaDB metrics + on-chain treasury balance → one Claude request → stored
 * row → Slack bridge.
 */
@Service
public class OwnerDigestService implements IOwnerDigestService {

  static final int MAX_TOKENS = 600;

  static final String SYSTEM_PROMPT =
      "You are the operations analyst for EH8S (Enigma H8 Studios), a music platform that pays"
          + " concerts, academy plans and royalties in USDC on Solana DevNet. Write a short daily"
          + " digest for the owner: 3 to 5 bullet points on what the numbers show, then one"
          + " recommended next action. Use only the numbers in the metrics JSON; when a number is"
          + " zero or null, say so plainly and do not invent activity. Plain text, no headings,"
          + " under 150 words.";

  private final IOwnerDigestRepository digestRepository;
  private final IClaudeClient claudeClient;
  private final ITreasuryService treasuryService;
  private final ISlackBridgeService slackBridgeService;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the service.
   *
   * @param digestRepository metrics and stored digests
   * @param claudeClient Anthropic Messages API client
   * @param treasuryService live treasury balance
   * @param slackBridgeService owner Slack bridge
   */
  public OwnerDigestService(
      IOwnerDigestRepository digestRepository,
      IClaudeClient claudeClient,
      ITreasuryService treasuryService,
      ISlackBridgeService slackBridgeService) {
    this.digestRepository = digestRepository;
    this.claudeClient = claudeClient;
    this.treasuryService = treasuryService;
    this.slackBridgeService = slackBridgeService;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> latest(String walletPubkey) {
    requireOwner(walletPubkey);
    Map<String, Object> out = new HashMap<>();
    out.put("aiConfigured", claudeClient.isConfigured());
    out.put("model", claudeClient.model());
    out.put("latest", digestRepository.latest().orElse(null));
    return out;
  }

  /** {@inheritDoc} */
  @Override
  public OwnerDigest run(String walletPubkey) {
    String wallet = requireOwner(walletPubkey);
    if (!claudeClient.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI digest unavailable: ANTHROPIC_API_KEY not set");
    }
    Map<String, Object> metrics = gatherMetrics(wallet, LocalDateTime.now());
    String metricsJson = toJson(metrics);
    Map<String, Object> reply =
        claudeClient.complete(SYSTEM_PROMPT, buildPrompt(metricsJson), MAX_TOKENS);
    String text = String.valueOf(reply.get("text"));

    OwnerDigest row = new OwnerDigest();
    row.setOwnerWalletPubkey(wallet);
    row.setModel(String.valueOf(reply.get("model")));
    row.setMetricsJson(metricsJson);
    row.setBody(text);
    row.setInputTokens((Integer) reply.get("inputTokens"));
    row.setOutputTokens((Integer) reply.get("outputTokens"));
    row.setSlackStatus(postToSlack(wallet, text));
    row.setCreatedAt(LocalDateTime.now());
    return digestRepository.insert(row);
  }

  /**
   * User message sent to Claude: the metrics JSON and the task.
   *
   * @param metricsJson serialized metrics map
   * @return prompt text
   */
  static String buildPrompt(String metricsJson) {
    return "EH8S platform metrics for the last 24 hours (JSON):\n"
        + metricsJson
        + "\n\nWrite today's owner digest.";
  }

  Map<String, Object> gatherMetrics(String ownerWallet, LocalDateTime now) {
    LocalDateTime since = now.minusHours(24);
    Map<String, Object> academy = new LinkedHashMap<>();
    academy.put("total", digestRepository.countAcademySubscriptions());
    academy.put("paidLast24h", digestRepository.countAcademyPaidSince(since));
    academy.put("onChainConfirmed", digestRepository.countAcademyConfirmed());
    Map<String, Object> settlements = new LinkedHashMap<>();
    settlements.put("total", digestRepository.countSettlements());
    settlements.put("onChainSettled", digestRepository.countSettlementsConfirmed());
    Map<String, Object> claimsByStatus = new LinkedHashMap<>();
    for (String status : digestRepository.findClaimStatuses()) {
      claimsByStatus.put(status, digestRepository.countClaims(status));
    }
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("windowStart", since.toString());
    metrics.put("academySubscriptions", academy);
    metrics.put("concertSettlements", settlements);
    metrics.put("pendingClaimsByStatus", claimsByStatus);
    metrics.put("bandVaultsActive", digestRepository.countActiveBandVaults());
    metrics.put("agentEventsLast24h", digestRepository.countAgentEventsSince(since));
    Object balance = null;
    try {
      balance = treasuryService.treasury(ownerWallet).get("balanceUsdc");
    } catch (RuntimeException e) {
      // RPC down: the digest reports the balance as unknown instead of failing.
    }
    metrics.put("treasuryBalanceUsdc", balance);
    return metrics;
  }

  private String postToSlack(String wallet, String text) {
    try {
      SlackDeliveryLog log =
          slackBridgeService.notify(wallet, "EH8S owner digest\n" + text, null, null);
      return log == null ? null : log.getStatus();
    } catch (RuntimeException e) {
      return "failed";
    }
  }

  private String toJson(Map<String, Object> metrics) {
    try {
      return mapper.writeValueAsString(metrics);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode metrics");
    }
  }

  private String requireOwner(String walletPubkey) {
    ChainConfig cfg =
        digestRepository
            .findActiveChainConfig()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No active chain config"));
    if (walletPubkey == null || walletPubkey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletPubkey is required");
    }
    String owner = cfg.getOwnerWalletPubkey();
    if (owner == null || !owner.equals(walletPubkey.trim())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner wallet can use the AI digest");
    }
    return owner;
  }
}
