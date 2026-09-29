package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for DevNet pay/claim recording.
 */
public interface IDevnetPayController {

  /**
   * @return markers
   */
  List<DevnetPayMarker> markers();

  /**
   * @param marker body
   * @return stored marker
   */
  DevnetPayMarker recordMarker(DevnetPayMarker marker);

  /**
   * @param claimId claim id
   * @param body walletPubkey + optional txSignature
   * @return updated claim
   */
  PendingClaim recordClaim(Long claimId, Map<String, Object> body);

  /**
   * @param subscriptionId subscription id
   * @param body walletPubkey + optional txSignature
   * @return updated subscription
   */
  AcademySubscription recordPay(Long subscriptionId, Map<String, Object> body);
}
