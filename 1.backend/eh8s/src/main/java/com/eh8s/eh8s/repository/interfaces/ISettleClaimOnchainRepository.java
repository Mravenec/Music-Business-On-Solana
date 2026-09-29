package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.ChainConfig;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.util.Optional;

/**
 * Persistence for DevNet settle_concert and claim_royalties confirmation (tx + PDA linkage).
 */
public interface ISettleClaimOnchainRepository {

  /**
   * Loads a concert settlement by id.
   *
   * @param settlementId row id
   * @return settlement when present
   */
  Optional<ConcertSettlement> findSettlement(Long settlementId);

  /**
   * Loads a pending claim by id.
   *
   * @param claimId row id
   * @return claim when present
   */
  Optional<PendingClaim> findClaim(Long claimId);

  /**
   * Loads the active chain_config row.
   *
   * @return active config when present
   */
  Optional<ChainConfig> findActiveChainConfig();

  /**
   * Loads wallet for the first pending claim on a settlement (musician account).
   *
   * @param settlementId settlement id
   * @return wallet pubkey when a claim exists
   */
  Optional<String> findFirstClaimWallet(Long settlementId);

  /**
   * Marks a settlement confirmed after a DevNet settle_concert signature.
   *
   * @param settlementId row id
   * @param walletPubkey venue signer wallet
   * @param txSignature confirmed DevNet signature
   * @param concertSettlementPda ConcertSettlement PDA
   * @return updated settlement
   */
  ConcertSettlement markSettlementConfirmed(
      Long settlementId, String walletPubkey, String txSignature, String concertSettlementPda);

  /**
   * Marks a claim confirmed after a DevNet claim_royalties signature that transferred USDC.
   *
   * @param claimId row id
   * @param walletPubkey claimer wallet
   * @param txSignature confirmed DevNet signature
   * @param claimPda claim / musician-profile PDA linkage
   * @return updated claim
   */
  PendingClaim markClaimConfirmed(
      Long claimId, String walletPubkey, String txSignature, String claimPda);

  /**
   * Whether this signature is already stored on a settlement or a claim.
   *
   * @param txSignature DevNet transaction signature
   * @return true when a row already holds it
   */
  default boolean isRecorded(String txSignature) {
    return false;
  }
}
