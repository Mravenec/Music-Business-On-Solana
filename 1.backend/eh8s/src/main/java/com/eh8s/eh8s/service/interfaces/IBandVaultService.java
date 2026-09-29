package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * On-chain BandVault lifecycle: activate (create_band), sync SPP weights (update_spp_weights),
 * and the SPP split that mirrors settle_concert on-chain.
 */
public interface IBandVaultService {

  /**
   * Returns the band with its vault columns.
   *
   * @param bandId band id
   * @return band
   */
  Band vault(Long bandId);

  /**
   * Builds create_band for the owner wallet. Refuses until every member has a wallet and an
   * enrolled MusicianProfile PDA, or when the vault already exists.
   *
   * @param bandId band id
   * @param walletPubkey signer wallet (base58)
   * @return wallet-ready instruction
   */
  Map<String, Object> buildActivate(Long bandId,String walletPubkey);

  /**
   * Confirms create_band on DevNet and stores the vault PDA, signature, and equal weights.
   *
   * @param bandId band id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @param bandVaultPda band vault PDA created by the transaction
   * @return updated band
   */
  Band confirmActivate(Long bandId,String walletPubkey, String txSignature, String bandVaultPda);

  /**
   * Builds update_spp_weights from the latest closed SPP cycle (equal split when none).
   *
   * @param bandId band id
   * @param walletPubkey signer wallet (base58)
   * @return wallet-ready instruction
   */
  Map<String, Object> buildSyncWeights(Long bandId,String walletPubkey);

  /**
   * Confirms update_spp_weights on DevNet and stores the synced weights.
   *
   * @param bandId band id
   * @param walletPubkey signer wallet (base58)
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return updated band
   */
  Band confirmSyncWeights(Long bandId,String walletPubkey, String txSignature);

  /**
   * Parses the synced weights of a band in vault order.
   *
   * @param band band row
   * @return rows {@code musicianProfileId}, {@code wallet}, {@code bps}; empty when no vault
   */
  List<Map<String, Object>> syncedWeights(Band band);

  /**
   * Pending claims that mirror the on-chain split of settle_concert: pool = net − floor(net ×
   * fee bps / 10000) in micro-USDC, member i = floor(pool × w_i / 10000), last takes the
   * remainder; each stored amount is floored to the cent so it never exceeds the on-chain pending.
   *
   * @param band band with a synced vault
   * @param netUsdc settlement net (gross − expenses)
   * @return unsaved claims in vault order (empty when the band has no vault)
   */
  List<PendingClaim> claimsFromVault(Band band, BigDecimal netUsdc);
}
