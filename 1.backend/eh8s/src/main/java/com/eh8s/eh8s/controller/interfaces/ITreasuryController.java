package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.TreasuryWithdrawal;
import java.util.Map;

/**
 * HTTP API for the owner treasury: live balance, withdraw build, verified withdraw record.
 */
public interface ITreasuryController {

  /**
   * Treasury PDA ATA balance, recent fee inflows, and withdrawals for the owner wallet.
   *
   * @param walletPubkey caller wallet (config owner)
   * @return treasury view
   */
  Map<String, Object> treasury(String walletPubkey);

  /**
   * Builds withdraw_treasury for the owner wallet.
   *
   * @param body {@code walletPubkey}, {@code amountUsdc}
   * @return wallet-ready instruction
   */
  Map<String, Object> buildWithdraw(Map<String, Object> body);

  /**
   * Confirms withdraw_treasury on DevNet and records it.
   *
   * @param body {@code walletPubkey}, {@code amountUsdc}, {@code txSignature}
   * @return stored withdrawal
   */
  TreasuryWithdrawal confirmWithdraw(Map<String, Object> body);
}
