package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IDevnetPayController;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.DevnetPayMarker;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.service.interfaces.IDevnetPayService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves DevNet pay/claim ledger JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class DevnetPayController implements IDevnetPayController {

  private final IDevnetPayService devnetPayService;

  /**
   * Creates the controller.
   *
   * @param devnetPayService pay use cases
   */
  public DevnetPayController(IDevnetPayService devnetPayService) {
    this.devnetPayService = devnetPayService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/devnet-pay-markers")
  public List<DevnetPayMarker> markers() {
    return devnetPayService.markers();
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/devnet-pay-markers")
  public DevnetPayMarker recordMarker(@RequestBody DevnetPayMarker marker) {
    return devnetPayService.recordMarker(marker);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/pending-claims/{claimId}/devnet-claim")
  public PendingClaim recordClaim(
      @PathVariable Long claimId, @RequestBody Map<String, Object> body) {
    return devnetPayService.recordClaim(
        claimId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/academy-subscriptions/{subscriptionId}/devnet-pay")
  public AcademySubscription recordPay(
      @PathVariable Long subscriptionId, @RequestBody Map<String, Object> body) {
    return devnetPayService.recordPay(
        subscriptionId, HttpBody.raw(body, "walletPubkey"));
  }
}
