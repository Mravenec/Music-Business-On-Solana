package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IStudioPowerOnchainController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.PartnerShare;
import com.eh8s.eh8s.database.jooq.eh8s_onchain.tables.pojos.StudioRoleGrant;
import com.eh8s.eh8s.service.interfaces.IStudioPowerOnchainService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the principal's studio power build/confirm JSON. Needs a Bearer session like every
 * {@code /api/**} route; the wallet signature (verified on DevNet) is what authorizes the change.
 */
@RestController
@RequestMapping("/api/studio-power")
public class StudioPowerOnchainController implements IStudioPowerOnchainController {

  private final IStudioPowerOnchainService studioPowerService;

  /**
   * Creates the controller.
   *
   * @param studioPowerService studio power use cases
   */
  public StudioPowerOnchainController(IStudioPowerOnchainService studioPowerService) {
    this.studioPowerService = studioPowerService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/console")
  public Map<String, Object> console(@RequestParam(required = false) String walletPubkey) {
    return studioPowerService.console(walletPubkey);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/grant/build")
  public Map<String, Object> buildGrant(@RequestBody Map<String, Object> body) {
    return studioPowerService.buildGrantStudioRole(
        HttpBody.raw(body, "walletPubkey"), HttpBody.raw(body, "adminWalletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/grant/confirm")
  public StudioRoleGrant confirmGrant(@RequestBody Map<String, Object> body) {
    return studioPowerService.confirmGrantStudioRole(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "adminWalletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/revoke/build")
  public Map<String, Object> buildRevoke(@RequestBody Map<String, Object> body) {
    return studioPowerService.buildRevokeStudioRole(
        HttpBody.raw(body, "walletPubkey"), HttpBody.raw(body, "adminWalletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/revoke/confirm")
  public StudioRoleGrant confirmRevoke(@RequestBody Map<String, Object> body) {
    return studioPowerService.confirmRevokeStudioRole(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "adminWalletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/partner-share/build")
  public Map<String, Object> buildPartnerShare(@RequestBody Map<String, Object> body) {
    return studioPowerService.buildSetPartnerShare(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "partnerWalletPubkey"),
        HttpBody.intValue(body, "shareBps"),
        HttpBody.longValue(body, "startsAt"),
        HttpBody.longValue(body, "endsAt"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/partner-share/confirm")
  public PartnerShare confirmPartnerShare(@RequestBody Map<String, Object> body) {
    return studioPowerService.confirmSetPartnerShare(
        HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "partnerWalletPubkey"),
        HttpBody.intValue(body, "shareBps"),
        HttpBody.longValue(body, "startsAt"),
        HttpBody.longValue(body, "endsAt"),
        HttpBody.raw(body, "txSignature"));
  }
}
