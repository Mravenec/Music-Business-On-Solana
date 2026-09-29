package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChainConfigService}.
 */
class ChainConfigServiceTest {

  @Test
  void activeReturnsRepositoryRow() {
    ChainConfig row = new ChainConfig();
    row.setId(1L);
    row.setNetwork("devnet");
    row.setRpcUrl("https://api.devnet.solana.com");
    row.setProgramIdDevnet("GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG");
    row.setAnchorProgramName("eh8s_devnet");
    row.setAnchorScaffoldPath("4.anchor/eh8s-devnet");
    row.setAnchorIdlVersion("0.2.0");
    row.setUsdcMint("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU");
    row.setEnvNetworkKey("VITE_NETWORK");
    row.setEnvRpcKey("VITE_SOLANA_RPC");
    row.setEnvProgramIdDevnetKey("VITE_BCF_PROGRAM_ID_DEVNET");
    row.setEnvProgramIdMainnetKey("VITE_BCF_PROGRAM_ID_MAINNET");
    row.setBagsApiBase("https://public-api-v2.bags.fm/api/v1");
    row.setOwnerWalletPubkey("7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC");
    row.setProtocolFeeBps(1500);
    row.setIsActive((byte) 1);
    IChainConfigRepository repo = () -> Optional.of(row);
    ChainConfigService service = new ChainConfigService(repo);
    assertEquals("devnet", service.active().getNetwork());
    assertEquals("VITE_NETWORK", service.active().getEnvNetworkKey());
    assertEquals("VITE_SOLANA_RPC", service.active().getEnvRpcKey());
    assertEquals("eh8s_devnet", service.anchorMetadata().get("anchorProgramName"));
  }
}
