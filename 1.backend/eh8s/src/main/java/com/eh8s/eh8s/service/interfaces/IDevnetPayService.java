package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.util.List;
import java.util.Map;

/**
 * DevNet pay/claim recording use cases (MariaDB markers; RPC optional).
 */
public interface IDevnetPayService {

  /**
   * @return ledger markers
   */
  List<DevnetPayMarker> markers();

  /**
   * Records a marker only when a DevNet txSignature is present. Intended/unsigned notes are refused.
   *
   * @param marker body including walletPubkey and txSignature
   * @return stored marker
   */
  DevnetPayMarker recordMarker(DevnetPayMarker marker);

  /**
   * Records a claim with optional tx signature.
   *
   * @param claimId claim id
   * @param walletPubkey signer wallet (base58)
   * @return updated claim
   */
  PendingClaim recordClaim(Long claimId,String walletPubkey);

  /**
   * Records an academy subscription pay with optional tx signature.
   *
   * @param subscriptionId subscription id
   * @param walletPubkey signer wallet (base58)
   * @return updated subscription
   */
  AcademySubscription recordPay(Long subscriptionId,String walletPubkey);
}
