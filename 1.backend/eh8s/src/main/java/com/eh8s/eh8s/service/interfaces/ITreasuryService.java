package com.eh8s.eh8s.service.interfaces;

import java.math.BigDecimal;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import java.util.Map;

/**
 * Owner treasury use cases: live on-chain balance of the treasury PDA ATA, withdraw build, and
 * verified withdraw record. Every call requires the config owner wallet (403 otherwise).
 */
public interface ITreasuryService {

  /**
   * Treasury PDA, its USDC ATA, the live balance, recent fee inflows, and recorded withdrawals.
   *
   * @param walletPubkey caller wallet (must be the config owner)
   * @return treasury view
   */
  Map<String, Object> treasury(String walletPubkey);

  /**
   * Builds withdraw_treasury for the owner wallet. 409 when the amount exceeds the live balance.
   *
   * @param walletPubkey signer wallet (base58)
   * @param amountUsdc USDC amount
   * @return wallet-ready instruction
   */
  Map<String, Object> buildWithdraw(String walletPubkey, BigDecimal amountUsdc);

  /**
   * Verifies the signed withdraw_treasury on DevNet (instruction data, owner signer, config and
   * treasury PDAs) and records it.
   *
   * @param walletPubkey signer wallet (base58)
   * @param amountUsdc USDC amount
   * @param txSignature DevNet-confirmed transaction signature (refused when already recorded)
   * @return stored withdrawal
   */
  TreasuryWithdrawal confirmWithdraw(String walletPubkey, BigDecimal amountUsdc, String txSignature);
}
