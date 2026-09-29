package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.util.Map;

/**
 * HTTP contract for DevNet settle_concert and claim_royalties build/confirm.
 */
public interface ISettleClaimOnchainController {

  /**
   * Builds settle_concert instruction metadata for wallet signing.
   *
   * @param settlementId concert_settlement id
   * @param body wallet and optional amount fields
   * @return instruction payload
   */
  Map<String, Object> buildSettleConcert(Long settlementId, Map<String, Object> body);

  /**
   * Confirms a DevNet settle_concert tx and links the settlement PDA.
   *
   * @param settlementId concert_settlement id
   * @param body walletPubkey, txSignature, concertSettlementPda
   * @return updated settlement
   */
  ConcertSettlement confirmSettleConcert(Long settlementId, Map<String, Object> body);

  /**
   * Builds claim_royalties instruction metadata for wallet signing.
   *
   * @param claimId pending_claim id
   * @param body wallet and optional ATA fields
   * @return instruction payload
   */
  Map<String, Object> buildClaimRoyalties(Long claimId, Map<String, Object> body);

  /**
   * Confirms a DevNet claim_royalties tx (USDC transfer) and marks the claim claimed.
   *
   * @param claimId pending_claim id
   * @param body walletPubkey, txSignature, claimPda
   * @return updated claim
   */
  PendingClaim confirmClaimRoyalties(Long claimId, Map<String, Object> body);
}
