package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.util.Map;

/**
 * Builds and verifies DevNet settle_concert and claim_royalties flows.
 */
public interface ISettleClaimOnchainService {

  /**
   * Builds settle_concert instruction metadata for a wallet to sign on DevNet.
   *
   * @param settlementId concert_settlement id
   * @param walletPubkey signer wallet (base58)
   * @return instruction payload
   */
  Map<String, Object> buildSettleConcert(Long settlementId,String walletPubkey);

  /**
   * Confirms a DevNet settle_concert signature via RPC and links the settlement PDA.
   *
   * @param settlementId concert_settlement id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @param concertSettlementPda concert settlement PDA created by the transaction
   * @return updated settlement with on_chain_status confirmed
   */
  ConcertSettlement confirmSettleConcert(Long settlementId,String walletPubkey, String txSignature, String concertSettlementPda);

  /**
   * Builds claim_royalties instruction metadata for a wallet to sign on DevNet.
   *
   * @param claimId pending_claim id
   * @param walletPubkey signer wallet (base58)
   * @param amountUsdc USDC amount
   * @param vaultUsdcAta optional vault USDC token account override
   * @param musicianUsdcAta optional musician USDC token account override
   * @return instruction payload
   */
  Map<String, Object> buildClaimRoyalties(Long claimId,String walletPubkey, BigDecimal amountUsdc, String vaultUsdcAta, String musicianUsdcAta);

  /**
   * Confirms a DevNet claim_royalties signature via RPC (USDC transfer) before marking claimed.
   *
   * @param claimId pending_claim id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @param claimPda claim PDA created by the transaction
   * @return updated claim with on_chain_status confirmed
   */
  PendingClaim confirmClaimRoyalties(Long claimId,String walletPubkey, String txSignature, String claimPda);
}
