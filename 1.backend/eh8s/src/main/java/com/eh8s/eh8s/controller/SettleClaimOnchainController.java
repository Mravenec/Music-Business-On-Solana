package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.ISettleClaimOnchainController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.service.interfaces.ISettleClaimOnchainService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves DevNet settle_concert and claim_royalties build/confirm JSON for the Stage wallet flow.
 */
@RestController
@RequestMapping("/api")
public class SettleClaimOnchainController implements ISettleClaimOnchainController {

  private final ISettleClaimOnchainService settleClaimOnchainService;

  /**
   * Creates the controller.
   *
   * @param settleClaimOnchainService on-chain settle/claim use cases
   */
  public SettleClaimOnchainController(ISettleClaimOnchainService settleClaimOnchainService) {
    this.settleClaimOnchainService = settleClaimOnchainService;
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/concert-settlements/{settlementId}/settle-concert/build")
  public Map<String, Object> buildSettleConcert(
      @PathVariable Long settlementId, @RequestBody Map<String, Object> body) {
    return settleClaimOnchainService.buildSettleConcert(
        settlementId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/concert-settlements/{settlementId}/settle-concert/confirm")
  public ConcertSettlement confirmSettleConcert(
      @PathVariable Long settlementId, @RequestBody Map<String, Object> body) {
    return settleClaimOnchainService.confirmSettleConcert(
        settlementId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "concertSettlementPda"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/pending-claims/{claimId}/claim-royalties/build")
  public Map<String, Object> buildClaimRoyalties(
      @PathVariable Long claimId, @RequestBody Map<String, Object> body) {
    return settleClaimOnchainService.buildClaimRoyalties(
        claimId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.decimal(body, "amountUsdc"),
        HttpBody.raw(body, "vaultUsdcAta"),
        HttpBody.raw(body, "musicianUsdcAta"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/pending-claims/{claimId}/claim-royalties/confirm")
  public PendingClaim confirmClaimRoyalties(
      @PathVariable Long claimId, @RequestBody Map<String, Object> body) {
    return settleClaimOnchainService.confirmClaimRoyalties(
        claimId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "claimPda"));
  }
}
