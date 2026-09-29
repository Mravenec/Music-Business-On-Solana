package com.eh8s.eh8s.repository.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import java.util.List;

/**
 * Persistence for owner-scoped Slack delivery attempts.
 */
public interface ISlackBridgeRepository {

  /**
   * Lists deliveries for a platform owner wallet.
   *
   * @param ownerWalletPubkey chain_config.owner_wallet_pubkey
   * @return newest first
   */
  List<SlackDeliveryLog> findByOwnerWallet(String ownerWalletPubkey);

  /**
   * Inserts a delivery attempt row.
   *
   * @param row delivery attempt
   * @return stored row with id
   */
  SlackDeliveryLog insert(SlackDeliveryLog row);
}
