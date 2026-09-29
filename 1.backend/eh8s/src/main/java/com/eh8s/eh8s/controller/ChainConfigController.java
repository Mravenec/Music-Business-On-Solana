package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IChainConfigController;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.service.interfaces.IChainConfigService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves Solana client settings and Anchor DevNet scaffold metadata.
 */
@RestController
@RequestMapping("/api")
public class ChainConfigController implements IChainConfigController {

  private final IChainConfigService chainConfigService;

  /**
   * Creates the controller.
   *
   * @param chainConfigService chain config use cases
   */
  public ChainConfigController(IChainConfigService chainConfigService) {
    this.chainConfigService = chainConfigService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/chain-config")
  public ChainConfig active() {
    return chainConfigService.active();
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/anchor/metadata")
  public Map<String, Object> anchorMetadata() {
    return chainConfigService.anchorMetadata();
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/platform/config")
  public Map<String, Object> platformConfig() {
    return chainConfigService.platformConfig();
  }
}
