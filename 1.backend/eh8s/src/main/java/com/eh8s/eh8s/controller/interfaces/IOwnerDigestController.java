package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDigest;
import java.util.Map;

/**
 * HTTP API for the Claude owner digest (JWT Bearer + owner wallet).
 */
public interface IOwnerDigestController {

  /**
   * Latest digest and AI availability.
   *
   * @param walletPubkey owner wallet
   * @return {@code aiConfigured}, {@code model}, {@code latest}
   */
  Map<String, Object> latest(String walletPubkey);

  /**
   * Generates today's digest with Claude.
   *
   * @param body command body {@code {walletPubkey}}; only that string reaches the service
   * @return stored digest
   */
  OwnerDigest run(Map<String, Object> body);
}
