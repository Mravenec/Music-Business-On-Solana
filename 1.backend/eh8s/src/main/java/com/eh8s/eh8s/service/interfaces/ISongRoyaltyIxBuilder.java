package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Builds per-song royalty pool / deposit / sync-pay instructions (the backend never signs).
 */
public interface ISongRoyaltyIxBuilder {

  /**
   * Builds {@code create_royalty_pool(track_id, members, splits_bps)}.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param signerWallet owner or WAVE agent (signer, pays pool rent)
   * @param trackId MariaDB track id
   * @param memberWallets member wallets in pool order
   * @param splitsBps member splits in pool order (sum 10000)
   * @param agentAccount AgentAuthority PDA or program id (owner)
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildCreatePool(
      String programId,
      String rpcUrl,
      String signerWallet,
      long trackId,
      List<String> memberWallets,
      List<Integer> splitsBps,
      String agentAccount);

  /**
   * Builds {@code deposit_royalties(track_id, amount)}. Remaining accounts are the member
   * MusicianProfile PDAs in pool order.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param depositorWallet owner or WAVE agent (signer, source of USDC)
   * @param trackId MariaDB track id
   * @param amountAtomic USDC amount (6 decimals)
   * @param agentAccount AgentAuthority PDA or program id (owner)
   * @param memberWallets pool members in pool order
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildDeposit(
      String programId,
      String rpcUrl,
      String usdcMint,
      String depositorWallet,
      long trackId,
      long amountAtomic,
      String agentAccount,
      List<String> memberWallets);

  /**
   * Builds {@code pay_sync_license(track_id, deal_id, amount)}: 20% to treasury, 80% credited to
   * the song pool members. Remaining accounts are the member MusicianProfile PDAs in pool order.
   *
   * @param programId DevNet program id
   * @param rpcUrl DevNet RPC
   * @param usdcMint DevNet USDC mint
   * @param payerWallet licensee wallet (signer, pays USDC and license rent)
   * @param trackId MariaDB track id
   * @param dealId MariaDB sync_license_deal id
   * @param amountAtomic gross license USDC (6 decimals)
   * @param memberWallets pool members in pool order
   * @return instruction payload for the React wallet adapter
   */
  Map<String, Object> buildSyncPay(
      String programId,
      String rpcUrl,
      String usdcMint,
      String payerWallet,
      long trackId,
      long dealId,
      long amountAtomic,
      List<String> memberWallets);
}
