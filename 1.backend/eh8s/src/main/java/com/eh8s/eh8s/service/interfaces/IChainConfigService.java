package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.util.Map;

/**
 * Solana client configuration use cases. Does not deploy a program.
 */
public interface IChainConfigService {

  /**
   * Returns the active dual-network client settings (RPC, program ids, env keys).
   *
   * @return the active chain_config row
   */
  ChainConfig active();

  /**
   * Anchor DevNet program metadata from chain_config (scaffold only).
   *
   * @return metadata for docs / FE page
   */
  Map<String, Object> anchorMetadata();

  /**
   * Public platform owner + protocol fee view for the product shell.
   *
   * @return owner wallet, fee bps, network
   */
  Map<String, Object> platformConfig();
}
