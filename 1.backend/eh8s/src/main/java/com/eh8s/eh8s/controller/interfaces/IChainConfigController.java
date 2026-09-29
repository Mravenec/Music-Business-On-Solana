package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.util.Map;

/**
 * HTTP contract for Solana client configuration.
 */
public interface IChainConfigController {

  /**
   * @return the active dual-network config
   */
  ChainConfig active();

  /**
   * @return Anchor DevNet scaffold metadata
   */
  Map<String, Object> anchorMetadata();

  /**
   * @return platform owner wallet and protocol fee
   */
  Map<String, Object> platformConfig();
}
