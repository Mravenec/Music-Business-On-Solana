package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import java.util.Map;

/**
 * HTTP API for the on-chain BandVault: activate (create_band) and sync SPP weights.
 */
public interface IBandVaultController {

  /**
   * Band with vault PDA, signatures, and synced weights JSON.
   *
   * @param bandId band id
   * @return band
   */
  Band vault(Long bandId);

  /**
   * Builds create_band for the owner wallet.
   *
   * @param bandId band id
   * @param body {@code walletPubkey}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildActivate(Long bandId, Map<String, Object> body);

  /**
   * Confirms create_band on DevNet.
   *
   * @param bandId band id
   * @param body {@code walletPubkey}, {@code txSignature}, {@code bandVaultPda}
   * @return updated band
   */
  Band confirmActivate(Long bandId, Map<String, Object> body);

  /**
   * Builds update_spp_weights for the owner wallet.
   *
   * @param bandId band id
   * @param body {@code walletPubkey}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildSyncWeights(Long bandId, Map<String, Object> body);

  /**
   * Confirms update_spp_weights on DevNet.
   *
   * @param bandId band id
   * @param body {@code walletPubkey}, {@code txSignature}
   * @return updated band
   */
  Band confirmSyncWeights(Long bandId, Map<String, Object> body);
}
