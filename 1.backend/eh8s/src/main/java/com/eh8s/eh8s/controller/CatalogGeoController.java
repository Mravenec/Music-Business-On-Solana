package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.ICatalogGeoController;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.ChannelPiece;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoRegion;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyType;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.TourPlan;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.service.interfaces.ICatalogGeoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves catalog, channel, and geo JSON for the React shell.
 */
@RestController
@RequestMapping("/api")
public class CatalogGeoController implements ICatalogGeoController {

  private final ICatalogGeoService catalogGeoService;

  public CatalogGeoController(ICatalogGeoService catalogGeoService) {
    this.catalogGeoService = catalogGeoService;
  }

  @Override
  @GetMapping("/tracks")
  public List<Track> tracks() {
    return catalogGeoService.tracks();
  }

  @Override
  @PostMapping("/tracks")
  public Track createTrack(@RequestBody Track track) {
    return catalogGeoService.createTrack(track);
  }

  @Override
  @GetMapping("/tracks/{trackId}/splits")
  public List<RoyaltySplit> splits(@PathVariable Long trackId) {
    return catalogGeoService.splits(trackId);
  }

  @Override
  @GetMapping("/royalty-types")
  public List<RoyaltyType> royaltyTypes() {
    return catalogGeoService.royaltyTypes();
  }

  @Override
  @GetMapping("/channel-pieces")
  public List<ChannelPiece> channelPieces() {
    return catalogGeoService.channelPieces();
  }

  @Override
  @PostMapping("/channel-pieces")
  public ChannelPiece createChannelPiece(@RequestBody ChannelPiece piece) {
    return catalogGeoService.createChannelPiece(piece);
  }

  @Override
  @GetMapping("/geo-tiers")
  public List<GeoTier> geoTiers() {
    return catalogGeoService.geoTiers();
  }

  @Override
  @GetMapping("/geo-regions")
  public List<GeoRegion> geoRegions() {
    return catalogGeoService.geoRegions();
  }

  @Override
  @GetMapping("/geo-subscriptions")
  public List<GeographicSubscription> geoSubscriptions() {
    return catalogGeoService.geoSubscriptions();
  }

  @Override
  @PostMapping("/geo-subscriptions")
  public GeographicSubscription subscribeGeo(@RequestBody GeographicSubscription subscription) {
    return catalogGeoService.subscribeGeo(subscription);
  }

  @Override
  @GetMapping("/tour-plans")
  public List<TourPlan> tourPlans() {
    return catalogGeoService.tourPlans();
  }

  @Override
  @PostMapping("/tour-plans")
  public TourPlan createTourPlan(@RequestBody TourPlan plan) {
    return catalogGeoService.createTourPlan(plan);
  }

  @Override
  @GetMapping("/royalty-deposits")
  public List<RoyaltyDeposit> royaltyDeposits() {
    return catalogGeoService.royaltyDeposits();
  }

  @Override
  @PostMapping("/royalty-deposits")
  public RoyaltyDeposit depositRoyalty(@RequestBody RoyaltyDeposit deposit) {
    return catalogGeoService.depositRoyalty(deposit);
  }
}
