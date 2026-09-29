package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import java.util.Map;

/**
 * Claude-written owner digest from live metrics (owner wallet only).
 */
public interface IOwnerDigestService {

  /**
   * Latest stored digest plus whether AI is configured.
   *
   * @param walletPubkey caller wallet (must be the platform owner)
   * @return {@code aiConfigured}, {@code model}, {@code latest} (OwnerDigest or null)
   */
  Map<String, Object> latest(String walletPubkey);

  /**
   * Gathers metrics, asks Claude for the digest, stores it, and posts it to Slack.
   *
   * @param walletPubkey caller wallet; must be the owner wallet (403 otherwise)
   * @return stored digest row
   * @throws org.springframework.web.server.ResponseStatusException 403 not the owner, 503 without
   *     ANTHROPIC_API_KEY
   */
  OwnerDigest run(String walletPubkey);
}
