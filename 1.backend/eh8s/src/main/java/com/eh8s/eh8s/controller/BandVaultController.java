package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.IBandVaultController;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.service.interfaces.IBandVaultService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves BandVault activation and SPP weight sync build/confirm JSON for the owner wallet.
 */
@RestController
@RequestMapping("/api")
public class BandVaultController implements IBandVaultController {

  private final IBandVaultService bandVaultService;

  /**
   * Creates the controller.
   *
   * @param bandVaultService band vault use cases
   */
  public BandVaultController(IBandVaultService bandVaultService) {
    this.bandVaultService = bandVaultService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/bands/{bandId}/vault")
  public Band vault(@PathVariable Long bandId) {
    return bandVaultService.vault(bandId);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bands/{bandId}/vault/build")
  public Map<String, Object> buildActivate(
      @PathVariable Long bandId, @RequestBody Map<String, Object> body) {
    return bandVaultService.buildActivate(
        bandId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bands/{bandId}/vault/confirm")
  public Band confirmActivate(@PathVariable Long bandId, @RequestBody Map<String, Object> body) {
    return bandVaultService.confirmActivate(
        bandId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.raw(body, "bandVaultPda"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bands/{bandId}/vault/weights/build")
  public Map<String, Object> buildSyncWeights(
      @PathVariable Long bandId, @RequestBody Map<String, Object> body) {
    return bandVaultService.buildSyncWeights(
        bandId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/bands/{bandId}/vault/weights/confirm")
  public Band confirmSyncWeights(
      @PathVariable Long bandId, @RequestBody Map<String, Object> body) {
    return bandVaultService.confirmSyncWeights(
        bandId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }
}
