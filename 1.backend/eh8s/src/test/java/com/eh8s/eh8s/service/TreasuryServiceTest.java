package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import com.eh8s.eh8s.repository.interfaces.ITreasuryRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import com.eh8s.eh8s.service.solana.GovernanceIxBuilder;
import com.eh8s.eh8s.service.solana.SettleClaimIxBuilder;
import com.eh8s.eh8s.service.solana.SolanaPda;
import com.eh8s.eh8s.service.solana.SolanaRpcClient;
import com.eh8s.eh8s.service.solana.WithdrawTreasuryIxBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TreasuryServiceTest {

  static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  static final String STRANGER = "YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU";
  static final String TREASURY_PDA = "DxGjk32dH8Jh5Xodx2syERsZJ6RPPaULxKsNmFmVWYWY";
  static final String TREASURY_ATA = "9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z";
  static final String CONFIG_PDA = "GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF";

  private final List<TreasuryWithdrawal> stored = new ArrayList<>();
  private final Map<String, Object> verified = new HashMap<>();

  @Test
  void builderDerivesDevnetTreasuryAccountsAndData() {
    Map<String, Object> ix =
        new WithdrawTreasuryIxBuilder()
            .build(
                "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG",
                "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU",
                "https://api.devnet.solana.com",
                OWNER,
                new BigDecimal("2.50"));
    assertEquals(TREASURY_PDA, ix.get("treasuryPda"));
    assertEquals(TREASURY_ATA, ix.get("treasuryUsdc"));
    assertEquals(2_500_000L, ix.get("amountAtomic"));
    String disc =
        HexFormat.of().formatHex(SettleClaimIxBuilder.anchorDiscriminator("withdraw_treasury"));
    assertEquals(disc + "a025260000000000", ix.get("dataHex"));
  }

  @Test
  void treasuryIsOwnerOnlyAndReadsLiveBalance() {
    TreasuryService service = service(12_340_000L);
    assertStatus(HttpStatus.FORBIDDEN, () -> service.treasury(STRANGER));
    assertStatus(HttpStatus.BAD_REQUEST, () -> service.treasury(null));
    Map<String, Object> view = service.treasury(OWNER);
    assertEquals(TREASURY_ATA, view.get("treasuryUsdc"));
    assertEquals(new BigDecimal("12.340000"), view.get("balanceUsdc"));
  }

  @Test
  void inflowsMergeThreeSourcesNewestFirstAndRespectLimit() {
    TreasuryService service = service(0L);
    List<Map<String, Object>> rows = service.recentInflows(20);
    assertEquals(List.of("geographic", "concert_fee", "academy"), rows.stream().map(r -> r.get("source")).toList());
    assertEquals(
        List.of("source", "recordId", "amountUsdc", "txSignature", "at"), List.copyOf(rows.get(0).keySet()));
    assertEquals(new BigDecimal("3.000000"), rows.get(0).get("amountUsdc"));
    assertEquals("sig-settle", rows.get(1).get("txSignature"));
    assertEquals(1L, rows.get(2).get("recordId"));
    assertEquals(2, service.recentInflows(2).size());
  }

  @Test
  void buildRejectsZeroAndOverBalance() {
    TreasuryService service = service(1_000_000L);
    assertStatus(
        HttpStatus.BAD_REQUEST,
        () -> OnchainCalls.buildWithdraw(service, Map.of("walletPubkey", OWNER, "amountUsdc", "0")));
    assertStatus(
        HttpStatus.CONFLICT,
        () -> OnchainCalls.buildWithdraw(service, Map.of("walletPubkey", OWNER, "amountUsdc", "1.01")));
    assertStatus(
        HttpStatus.FORBIDDEN,
        () -> OnchainCalls.buildWithdraw(service, Map.of("walletPubkey", STRANGER, "amountUsdc", "1")));
    Map<String, Object> ix = OnchainCalls.buildWithdraw(service, Map.of("walletPubkey", OWNER, "amountUsdc", "1"));
    assertEquals("withdraw_treasury", ix.get("instruction"));
  }

  @Test
  void confirmVerifiesOwnerTxThenRecords() {
    TreasuryService service = service(5_000_000L);
    TreasuryWithdrawal row =
        OnchainCalls.confirmWithdraw(service, 
            Map.of("walletPubkey", OWNER, "amountUsdc", "2.50", "txSignature", "sig-1"));
    assertEquals("withdraw_treasury", verified.get("instruction"));
    assertEquals(OWNER, verified.get("signer"));
    String program = "GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG";
    String mint = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";
    assertEquals(
        ISolanaTxVerifier.accounts(
            OWNER,
            CONFIG_PDA,
            TREASURY_PDA,
            SolanaPda.associatedTokenAddress(TREASURY_PDA, mint),
            SolanaPda.associatedTokenAddress(OWNER, mint),
            SolanaPda.TOKEN_PROGRAM,
            GovernanceIxBuilder.governancePda(program)),
        verified.get("accounts"));
    assertTrue(((String) verified.get("dataHex")).endsWith("a025260000000000"));
    assertEquals(new BigDecimal("2.500000"), row.getAmountUsdc());
    assertEquals(1, stored.size());
    assertStatus(
        HttpStatus.CONFLICT,
        () ->
            OnchainCalls.confirmWithdraw(
                service, Map.of("walletPubkey", OWNER, "amountUsdc", "2.50", "txSignature", "sig-1")));
  }

  private TreasuryService service(long balanceAtomic) {
    return new TreasuryService(repo(), new WithdrawTreasuryIxBuilder(), rpc(balanceAtomic), verifier());
  }

  private static void assertStatus(HttpStatus status, Runnable call) {
    ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
    assertEquals(status, ex.getStatusCode());
  }

  private static SolanaRpcClient rpc(long balanceAtomic) {
    return new SolanaRpcClient() {
      @Override
      public long getTokenAccountBalance(String rpcUrl, String tokenAccount) {
        assertEquals(TREASURY_ATA, tokenAccount);
        return balanceAtomic;
      }
    };
  }

  private ISolanaTxVerifier verifier() {
    return new ISolanaTxVerifier() {
      @Override
      public void verify(
          String rpcUrl,
          String programId,
          String txSignature,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        verified.put("instruction", instruction);
        verified.put("dataHex", expectedDataHex);
        verified.put("signer", expectedSigner);
        verified.put("accounts", expectedAccounts);
      }

      @Override
      public void verifyTransaction(
          JsonNode tx,
          String programId,
          String instruction,
          String expectedDataHex,
          String expectedSigner,
          List<String> expectedAccounts) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private ITreasuryRepository repo() {
    return new ITreasuryRepository() {
      @Override
      public Optional<ChainConfig> findActiveChainConfig() {
        ChainConfig cfg = new ChainConfig();
        cfg.setProgramIdDevnet("GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG");
        cfg.setUsdcMint("4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU");
        cfg.setRpcUrl("https://api.devnet.solana.com");
        cfg.setOwnerWalletPubkey(OWNER);
        return Optional.of(cfg);
      }

      @Override
      public List<AcademySubscription> findPaidAcademySubscriptions(int limit) {
        AcademySubscription s = new AcademySubscription();
        s.setId(1L);
        s.setTreasuryUsdc(new BigDecimal("8.500000"));
        s.setPayTxSignature("sig-academy");
        s.setPaidAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        return List.of(s);
      }

      @Override
      public List<GeographicSubscription> findPaidGeographicSubscriptions(int limit) {
        GeographicSubscription s = new GeographicSubscription();
        s.setId(2L);
        s.setGeoTierId(5L);
        s.setPayTxSignature("sig-geo");
        s.setPaidAt(LocalDateTime.of(2026, 9, 3, 10, 0));
        return List.of(s);
      }

      @Override
      public List<GeoTier> findGeoTiersByIds(List<Long> ids) {
        GeoTier t = new GeoTier();
        t.setId(5L);
        t.setUsdcMonthly(new BigDecimal("3.000000"));
        return ids.contains(5L) ? List.of(t) : List.of();
      }

      @Override
      public List<ConcertSettlement> findSettledConcerts(int limit) {
        ConcertSettlement c = new ConcertSettlement();
        c.setId(3L);
        c.setEh8sFeeUsdc(new BigDecimal("101.250000"));
        c.setSettleTxSignature("sig-settle");
        c.setSettledAt(LocalDateTime.of(2026, 9, 2, 10, 0));
        return List.of(c);
      }

      @Override
      public List<TreasuryWithdrawal> listWithdrawals() {
        return stored;
      }

      @Override
      public TreasuryWithdrawal insertWithdrawal(TreasuryWithdrawal withdrawal) {
        withdrawal.setId((long) stored.size() + 1);
        stored.add(withdrawal);
        return withdrawal;
      }

      @Override
      public boolean isRecorded(String txSignature) {
        return stored.stream().anyMatch(row -> txSignature.equals(row.getTxSignature()));
      }
    };
  }
}
