package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for the BandVault SPP split mirror and weight normalization.
 */
class BandVaultServiceTest {

  @Test
  void claimsFromVaultFloorOnChainSharesToCents() {
    Band band = SettleClaimOnchainServiceTest.activeBand();
    List<PendingClaim> claims =
        SettleClaimOnchainServiceTest.bandVaultService(band)
            .claimsFromVault(band, new BigDecimal("675.00"));
    assertEquals(4, claims.size());
    assertEquals(new BigDecimal("218.02"), claims.get(0).getAmountUsdc());
    assertEquals(new BigDecimal("201.96"), claims.get(1).getAmountUsdc());
    assertEquals(new BigDecimal("114.75"), claims.get(2).getAmountUsdc());
    assertEquals(new BigDecimal("39.01"), claims.get(3).getAmountUsdc());
    assertEquals(11L, claims.get(0).getMusicianProfileId());
    assertEquals(3800, claims.get(0).getShareBps());
  }

  @Test
  void claimsFromVaultEmptyWithoutVault() {
    Band band = SettleClaimOnchainServiceTest.activeBand();
    band.setBandVaultPda(null);
    assertTrue(
        SettleClaimOnchainServiceTest.bandVaultService(band)
            .claimsFromVault(band, new BigDecimal("675.00"))
            .isEmpty());
  }

  @Test
  void normalizeSumsToTenThousand() {
    assertEquals(List.of(3800, 3520, 2000, 680), BandVaultService.normalize(List.of(380, 352, 200, 68)));
    assertEquals(List.of(3333, 3333, 3334), BandVaultService.normalize(List.of(1, 1, 1)));
    assertEquals(List.of(5000, 5000), BandVaultService.normalize(List.of(0, 0)));
  }

  @Test
  void syncWeightsWithoutVaultIsConflict() {
    Band band = SettleClaimOnchainServiceTest.activeBand();
    band.setBandVaultPda(null);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                OnchainCalls.buildSyncWeights(SettleClaimOnchainServiceTest.bandVaultService(band), 1L, Map.of("walletPubkey", SettleClaimOnchainServiceTest.OWNER)));
    assertEquals(409, ex.getStatusCode().value());
  }
}
