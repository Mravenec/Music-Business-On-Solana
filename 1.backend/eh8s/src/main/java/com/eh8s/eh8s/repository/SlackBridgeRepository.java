package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.SlackDeliveryLog;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.records.SlackDeliveryLogRecord;
import com.eh8s.eh8s.repository.interfaces.ISlackBridgeRepository;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.SLACK_DELIVERY_LOG;

/**
 * JOOQ persistence for owner-scoped Slack delivery log.
 */
@Repository
public class SlackBridgeRepository implements ISlackBridgeRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ DSL
   */
  public SlackBridgeRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SlackDeliveryLog> findByOwnerWallet(String ownerWalletPubkey) {
    return dsl.selectFrom(SLACK_DELIVERY_LOG)
        .where(SLACK_DELIVERY_LOG.OWNER_WALLET_PUBKEY.eq(ownerWalletPubkey))
        .orderBy(SLACK_DELIVERY_LOG.ID.desc())
        .fetchInto(SlackDeliveryLog.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SlackDeliveryLog insert(SlackDeliveryLog row) {
    SlackDeliveryLogRecord rec = dsl.newRecord(SLACK_DELIVERY_LOG, row);
    rec.changed(SLACK_DELIVERY_LOG.ID, false);
    rec.store();
    return rec.into(SlackDeliveryLog.class);
  }
}
