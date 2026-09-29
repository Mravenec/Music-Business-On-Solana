package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import java.util.Optional;

/**
 * Persistence for Solana client settings (BagsCreatorFund dual-network fields).
 */
public interface IChainConfigRepository {

  /**
   * Loads the active chain_config row.
   *
   * @return the active client config, or empty if none is marked active
   */
  Optional<ChainConfig> findActive();
}
