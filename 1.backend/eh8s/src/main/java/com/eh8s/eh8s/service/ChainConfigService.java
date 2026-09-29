package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import com.eh8s.eh8s.service.interfaces.IChainConfigService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads Solana DevNet settings from MariaDB for the EH8S Anchor program.
 */
@Service
public class ChainConfigService implements IChainConfigService {

  private static final List<String> DEFAULT_INSTRUCTIONS =
      List.of(
          "initialize_config",
          "upsert_musician_profile",
          "subscribe_academy",
          "settle_concert",
          "claim_royalties",
          "deposit_royalties",
          "subscribe_geographic");

  private final IChainConfigRepository chainConfigRepository;

  /**
   * Creates the service.
   *
   * @param chainConfigRepository chain_config persistence
   */
  public ChainConfigService(IChainConfigRepository chainConfigRepository) {
    this.chainConfigRepository = chainConfigRepository;
  }

  /** {@inheritDoc} */
  @Override
  public ChainConfig active() {
    return chainConfigRepository
        .findActive()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active chain_config"));
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> anchorMetadata() {
    ChainConfig cfg = active();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("network", cfg.getNetwork());
    body.put("rpcUrl", cfg.getRpcUrl());
    body.put("programIdDevnet", cfg.getProgramIdDevnet());
    body.put("programIdMainnet", cfg.getProgramIdMainnet());
    body.put(
        "anchorProgramName",
        blank(cfg.getAnchorProgramName()) ? "eh8s_devnet" : cfg.getAnchorProgramName());
    body.put(
        "anchorScaffoldPath",
        blank(cfg.getAnchorScaffoldPath())
            ? "4.anchor/eh8s-devnet"
            : cfg.getAnchorScaffoldPath());
    body.put(
        "anchorIdlVersion",
        blank(cfg.getAnchorIdlVersion()) ? "0.3.0" : cfg.getAnchorIdlVersion());
    body.put("usdcMint", cfg.getUsdcMint());
    body.put("squadsMultisig", cfg.getSquadsMultisig());
    body.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    body.put("protocolFeeBps", cfg.getProtocolFeeBps());
    body.put("instructions", DEFAULT_INSTRUCTIONS);
    body.put("mainnetDeployRequired", false);
    body.put(
        "note",
        "EH8S Anchor program source lives under 4.anchor/eh8s-devnet. Deploy to DevNet then set program_deployed_at. Wallet flows must use DevNet USDC mint from usdcMint.");
    return body;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> platformConfig() {
    ChainConfig cfg = active();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("network", cfg.getNetwork());
    body.put("ownerWalletPubkey", cfg.getOwnerWalletPubkey());
    body.put("protocolFeeBps", cfg.getProtocolFeeBps());
    body.put(
        "protocolFeePercent",
        cfg.getProtocolFeeBps() == null
            ? null
            : BigDecimal.valueOf(cfg.getProtocolFeeBps())
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
    body.put(
        "note",
        "Platform owner earns protocolFeeBps of eligible volume; other wallets use role-scoped product surfaces.");
    return body;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
