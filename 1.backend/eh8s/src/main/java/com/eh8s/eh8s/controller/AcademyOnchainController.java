package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IAcademyOnchainController;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.AcademySubscription;
import com.eh8s.eh8s.service.interfaces.IAcademyOnchainService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves DevNet subscribe_academy build/confirm JSON for the React wallet flow.
 */
@RestController
@RequestMapping("/api")
public class AcademyOnchainController implements IAcademyOnchainController {

  private final IAcademyOnchainService academyOnchainService;

  /**
   * Creates the controller.
   *
   * @param academyOnchainService on-chain academy use cases
   */
  public AcademyOnchainController(IAcademyOnchainService academyOnchainService) {
    this.academyOnchainService = academyOnchainService;
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/academy-subscriptions/{subscriptionId}/subscribe-academy/build")
  public Map<String, Object> build(
      @PathVariable Long subscriptionId, @RequestBody Map<String, Object> body) {
    return academyOnchainService.buildSubscribeAcademy(
        subscriptionId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "instructorUsdcAta"),
        HttpBody.raw(body, "payerUsdcAta"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/academy-subscriptions/{subscriptionId}/subscribe-academy/confirm")
  public AcademySubscription confirm(
      @PathVariable Long subscriptionId, @RequestBody Map<String, Object> body) {
    return academyOnchainService.confirmSubscribeAcademy(
        subscriptionId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "academySubscriptionPda"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/academy-subscriptions/{subscriptionId}/onchain")
  public Map<String, Object> onchainStatus(@PathVariable Long subscriptionId) {
    return academyOnchainService.onchainStatus(subscriptionId);
  }
}
