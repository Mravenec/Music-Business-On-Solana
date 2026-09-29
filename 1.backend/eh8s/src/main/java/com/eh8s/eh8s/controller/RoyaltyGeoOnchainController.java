package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IRoyaltyGeoOnchainController;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.service.interfaces.IRoyaltyGeoOnchainService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves DevNet deposit_royalties and subscribe_geographic build/confirm JSON for wallet flows.
 */
@RestController
@RequestMapping("/api")
public class RoyaltyGeoOnchainController implements IRoyaltyGeoOnchainController {

  private final IRoyaltyGeoOnchainService royaltyGeoOnchainService;

  /**
   * Creates the controller.
   *
   * @param royaltyGeoOnchainService on-chain royalty/geo use cases
   */
  public RoyaltyGeoOnchainController(IRoyaltyGeoOnchainService royaltyGeoOnchainService) {
    this.royaltyGeoOnchainService = royaltyGeoOnchainService;
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/royalty-deposits/{depositId}/deposit-royalties/build")
  public Map<String, Object> buildDeposit(
      @PathVariable Long depositId, @RequestBody Map<String, Object> body) {
    return royaltyGeoOnchainService.buildDepositRoyalties(
        depositId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/royalty-deposits/{depositId}/deposit-royalties/confirm")
  public RoyaltyDeposit confirmDeposit(
      @PathVariable Long depositId, @RequestBody Map<String, Object> body) {
    return royaltyGeoOnchainService.confirmDepositRoyalties(
        depositId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/geo-subscriptions/{subscriptionId}/subscribe-geographic/build")
  public Map<String, Object> buildGeo(
      @PathVariable Long subscriptionId, @RequestBody Map<String, Object> body) {
    return royaltyGeoOnchainService.buildSubscribeGeographic(
        subscriptionId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "payerUsdcAta"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/geo-subscriptions/{subscriptionId}/subscribe-geographic/confirm")
  public GeographicSubscription confirmGeo(
      @PathVariable Long subscriptionId, @RequestBody Map<String, Object> body) {
    return royaltyGeoOnchainService.confirmSubscribeGeographic(
        subscriptionId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "geographicSubscriptionPda"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/geo-subscriptions/{subscriptionId}/onchain")
  public Map<String, Object> geoOnchainStatus(@PathVariable Long subscriptionId) {
    return royaltyGeoOnchainService.onchainGeoStatus(subscriptionId);
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/geo-zones/{geoCode}/status")
  public Map<String, Object> zoneStatus(@PathVariable String geoCode, @RequestParam String wallet) {
    return royaltyGeoOnchainService.zoneStatus(wallet, geoCode);
  }
}
