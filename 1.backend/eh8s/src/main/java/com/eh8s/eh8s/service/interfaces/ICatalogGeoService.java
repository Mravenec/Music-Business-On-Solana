package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.ChannelPiece;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoRegion;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyType;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.TourPlan;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import java.util.List;

/**
 * Catalog, channel, and geographic subscription use cases.
 */
public interface ICatalogGeoService {

  /**
   * Lists every catalog track.
   *
   * @return tracks
   */
  List<Track> tracks();

  /**
   * Creates a catalog track (JOOQ POJO). Used so royalty deposits can run without SQL mocks.
   *
   * @param track title and optional bandId
   * @return stored track
   */
  Track createTrack(Track track);

  /**
   * Lists the royalty splits configured for one track.
   *
   * @param trackId track id
   * @return splits for that track
   */
  List<RoyaltySplit> splits(Long trackId);

  /**
   * Lists royalty types (master, mechanical, performance, sync).
   *
   * @return royalty types
   */
  List<RoyaltyType> royaltyTypes();

  /**
   * Lists channel pieces (livestream, VOD, masterclass, podcast) with their share splits.
   *
   * @return channel pieces
   */
  List<ChannelPiece> channelPieces();

  /**
   * Creates a channel piece.
   *
   * @param piece format, title and share basis points
   * @return stored channel piece
   */
  ChannelPiece createChannelPiece(ChannelPiece piece);

  /**
   * Lists geographic subscription tiers (Local to Global) with monthly USDC price.
   *
   * @return geo tiers
   */
  List<GeoTier> geoTiers();

  /**
   * Lists geo regions with the on-chain zone code used as the subscribe_geographic seed.
   *
   * @return geo regions
   */
  List<GeoRegion> geoRegions();

  /**
   * Lists geographic subscriptions (paid and unpaid).
   *
   * @return geo subscriptions
   */
  List<GeographicSubscription> geoSubscriptions();

  /**
   * Creates an unpaid geo subscription: tier, region zone code (or a finer code under it), and
   * months 1..12.
   *
   * @param subscription band, tier, region, optional geoCode and months
   * @return stored subscription
   */
  GeographicSubscription subscribeGeo(GeographicSubscription subscription);

  /**
   * Lists tour plans.
   *
   * @return tour plans
   */
  List<TourPlan> tourPlans();

  /**
   * Creates a tour plan.
   *
   * @param plan band, region, title and window
   * @return stored tour plan
   */
  TourPlan createTourPlan(TourPlan plan);

  /**
   * Lists royalty deposits.
   *
   * @return royalty deposits
   */
  List<RoyaltyDeposit> royaltyDeposits();

  /**
   * Records an off-chain royalty deposit for a track.
   *
   * @param deposit track, royalty type and amount
   * @return stored deposit
   */
  RoyaltyDeposit depositRoyalty(RoyaltyDeposit deposit);
}
