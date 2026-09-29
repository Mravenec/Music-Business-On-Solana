package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Builds create_band / update_spp_weights instructions (the backend never signs).
 */
public interface IBandVaultIxBuilder {

  /**
   * Builds create_band(band_id, members).
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer, pays rent)
   * @param bandId MariaDB band id (PDA seed)
   * @param memberWallets member wallet pubkeys in vault order
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildCreateBand(
      String programId, String rpcUrl, String ownerWallet, long bandId, List<String> memberWallets);

  /**
   * Builds update_spp_weights(weights_bps).
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param ownerWallet config owner (signer)
   * @param bandId MariaDB band id (PDA seed)
   * @param weightsBps weights in vault member order (sum 10000)
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildUpdateWeights(
      String programId, String rpcUrl, String ownerWallet, long bandId, List<Integer> weightsBps);
}
