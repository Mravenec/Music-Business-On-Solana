package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.repository.interfaces.IChainReconcileRepository;
import com.eh8s.eh8s.service.interfaces.ISolanaTxVerifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ChainReconcileServiceTest {

  private static final String OWNER = "7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC";
  private static final String RPC = "https://api.devnet.solana.com";
  private static final String GOOD_SIG = "GoodSig111111111111111111111111111111111111111";
  private static final String BAD_SIG = "BadSig2222222222222222222222222222222222222222";

  @Test
  void flagsBadRowAndLeavesGoodUntouched() {
    FakeRepo repo = new FakeRepo();
    ConcertSettlement good = settlement(1L, GOOD_SIG);
    ConcertSettlement bad = settlement(2L, BAD_SIG);
    repo.settlements.add(good);
    repo.settlements.add(bad);

    FakeVerifier verifier = new FakeVerifier();
    verifier.ok.add(GOOD_SIG);

    ChainReconcileService svc = new ChainReconcileService(repo, verifier);
    Map<String, Object> out = svc.reconcile(OWNER);

    assertEquals(2, out.get("scanned"));
    assertEquals(1, out.get("ok"));
    assertEquals(false, out.get("inventedUsdc"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> flagged = (List<Map<String, Object>>) out.get("flagged");
    assertEquals(1, flagged.size());
    assertEquals(2L, flagged.get(0).get("id"));
    assertEquals("concert_settlement", flagged.get(0).get("kind"));
    assertTrue(repo.demotedSettlements.contains(2L));
    assertFalse(repo.demotedSettlements.contains(1L));
    assertEquals("confirmed", good.getOnChainStatus());
  }

  @Test
  void demotesConfirmedRowWithMissingSignatureWithoutInventingUsdc() {
    FakeRepo repo = new FakeRepo();
    repo.settlements.add(settlement(9L, null));
    FakeVerifier verifier = new FakeVerifier();

    Map<String, Object> out = new ChainReconcileService(repo, verifier).reconcile(OWNER);

    assertEquals(1, out.get("scanned"));
    assertEquals(0, out.get("ok"));
    assertTrue(repo.demotedSettlements.contains(9L));
    assertTrue(verifier.calls.isEmpty());
    assertEquals(false, out.get("inventedUsdc"));
  }

  @Test
  void rpcFailureDoesNotDemote() {
    FakeRepo repo = new FakeRepo();
    repo.settlements.add(settlement(3L, GOOD_SIG));
    FakeVerifier verifier = new FakeVerifier();
    verifier.gatewayFail.add(GOOD_SIG);

    Map<String, Object> out = new ChainReconcileService(repo, verifier).reconcile(OWNER);

    assertEquals(1, out.get("scanned"));
    assertEquals(0, out.get("ok"));
    assertTrue(repo.demotedSettlements.isEmpty());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rpcErrors = (List<Map<String, Object>>) out.get("rpcErrors");
    assertEquals(1, rpcErrors.size());
  }

  @Test
  void nonOwnerForbidden() {
    FakeRepo repo = new FakeRepo();
    ChainReconcileService svc = new ChainReconcileService(repo, new FakeVerifier());
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> svc.reconcile("not-the-owner"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  @Test
  void scheduledUsesSameScanWithoutPrincipal() {
    FakeRepo repo = new FakeRepo();
    repo.settlements.add(settlement(1L, GOOD_SIG));
    repo.settlements.add(settlement(2L, BAD_SIG));
    FakeVerifier verifier = new FakeVerifier();
    verifier.ok.add(GOOD_SIG);

    Map<String, Object> out = new ChainReconcileService(repo, verifier).reconcileScheduled();
    assertEquals(2, out.get("scanned"));
    assertEquals(1, out.get("ok"));
    assertTrue(repo.demotedSettlements.contains(2L));
  }

  private static ConcertSettlement settlement(Long id, String sig) {
    ConcertSettlement row = new ConcertSettlement();
    row.setId(id);
    row.setSettleTxSignature(sig);
    row.setOnChainStatus("confirmed");
    row.setGrossUsdc(java.math.BigDecimal.TEN);
    return row;
  }

  private static final class FakeVerifier implements ISolanaTxVerifier {
    final Set<String> ok = new HashSet<>();
    final Set<String> gatewayFail = new HashSet<>();
    final List<String> calls = new ArrayList<>();

    @Override
    public void verify(
        String rpcUrl,
        String programId,
        String txSignature,
        String instruction,
        String expectedDataHex,
        String expectedSigner,
        List<String> expectedAccounts) {}

    @Override
    public void verifyTransaction(
        com.fasterxml.jackson.databind.JsonNode tx,
        String programId,
        String instruction,
        String expectedDataHex,
        String expectedSigner,
        List<String> expectedAccounts) {}

    @Override
    public void requireSuccessfulTransaction(String rpcUrl, String txSignature) {
      calls.add(txSignature);
      if (gatewayFail.contains(txSignature)) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "RPC down");
      }
      if (!ok.contains(txSignature)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "DevNet transaction not found or not confirmed yet");
      }
    }
  }

  private static final class FakeRepo implements IChainReconcileRepository {
    final List<ConcertSettlement> settlements = new ArrayList<>();
    final Set<Long> demotedSettlements = new HashSet<>();

    @Override
    public Optional<ChainConfig> findActiveChainConfig() {
      ChainConfig cfg = new ChainConfig();
      cfg.setOwnerWalletPubkey(OWNER);
      cfg.setRpcUrl(RPC);
      return Optional.of(cfg);
    }

    @Override
    public List<ConcertSettlement> listConfirmedSettlements() {
      return settlements.stream().filter(s -> "confirmed".equals(s.getOnChainStatus())).toList();
    }

    @Override
    public List<PendingClaim> listConfirmedClaims() {
      return List.of();
    }

    @Override
    public List<AcademySubscription> listConfirmedAcademySubscriptions() {
      return List.of();
    }

    @Override
    public List<GeographicSubscription> listConfirmedGeoSubscriptions() {
      return List.of();
    }

    @Override
    public List<RoyaltyDeposit> listConfirmedRoyaltyDeposits() {
      return List.of();
    }

    @Override
    public List<SyncLicenseDeal> listPaidSyncLicenseDeals() {
      return List.of();
    }

    @Override
    public boolean demoteSettlement(Long id) {
      demotedSettlements.add(id);
      settlements.stream()
          .filter(s -> id.equals(s.getId()))
          .forEach(s -> s.setOnChainStatus("orphaned"));
      return true;
    }

    @Override
    public boolean demoteClaim(Long id) {
      return false;
    }

    @Override
    public boolean demoteAcademySubscription(Long id) {
      return false;
    }

    @Override
    public boolean demoteGeoSubscription(Long id) {
      return false;
    }

    @Override
    public boolean demoteRoyaltyDeposit(Long id) {
      return false;
    }

    @Override
    public boolean demoteSyncLicenseDeal(Long id) {
      return false;
    }
  }
}
