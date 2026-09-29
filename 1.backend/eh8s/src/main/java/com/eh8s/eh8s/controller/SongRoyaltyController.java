package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.controller.interfaces.ISongRoyaltyController;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.SyncLicenseDeal;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.service.interfaces.ISongRoyaltyService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Serves per-song royalty pool, sync license deal and per-song credit JSON. */
@RestController
@RequestMapping("/api")
public class SongRoyaltyController implements ISongRoyaltyController {

  private final ISongRoyaltyService songRoyaltyService;

  /**
   * Creates the controller.
   *
   * @param songRoyaltyService per-song royalty use cases
   */
  public SongRoyaltyController(ISongRoyaltyService songRoyaltyService) {
    this.songRoyaltyService = songRoyaltyService;
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/tracks/{trackId}/royalty-pool")
  public Map<String, Object> poolStatus(@PathVariable Long trackId) {
    return songRoyaltyService.poolStatus(trackId);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/tracks/{trackId}/royalty-pool/build")
  public Map<String, Object> buildPool(
      @PathVariable Long trackId, @RequestBody Map<String, Object> body) {
    return songRoyaltyService.buildActivatePool(
        trackId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.royaltySplits(body, "splits"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/tracks/{trackId}/royalty-pool/confirm")
  public Track confirmPool(@PathVariable Long trackId, @RequestBody Map<String, Object> body) {
    return songRoyaltyService.confirmActivatePool(
        trackId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"),
        HttpBody.royaltySplits(body, "splits"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/sync-deals")
  public List<SyncLicenseDeal> deals(@RequestParam(required = false) Long trackId) {
    return songRoyaltyService.deals(trackId);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/sync-deals")
  public SyncLicenseDeal createDeal(@RequestBody SyncLicenseDeal deal) {
    return songRoyaltyService.createDeal(deal);
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/sync-deals/{dealId}/pay/build")
  public Map<String, Object> buildPay(
      @PathVariable Long dealId, @RequestBody Map<String, Object> body) {
    return songRoyaltyService.buildPaySync(
        dealId, HttpBody.raw(body, "walletPubkey"));
  }

  /** {@inheritDoc} */
  @Override
  @PostMapping("/sync-deals/{dealId}/pay/confirm")
  public SyncLicenseDeal confirmPay(
      @PathVariable Long dealId, @RequestBody Map<String, Object> body) {
    return songRoyaltyService.confirmPaySync(
        dealId, HttpBody.raw(body, "walletPubkey"),
        HttpBody.raw(body, "txSignature"));
  }

  /** {@inheritDoc} */
  @Override
  @GetMapping("/song-credits")
  public List<Map<String, Object>> songCredits(@RequestParam String walletPubkey) {
    return songRoyaltyService.songCredits(walletPubkey);
  }
}
