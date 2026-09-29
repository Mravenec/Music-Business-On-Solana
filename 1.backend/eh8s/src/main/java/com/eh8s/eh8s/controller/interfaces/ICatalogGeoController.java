package com.eh8s.eh8s.controller.interfaces;

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
 * HTTP contract for catalog, channel, and geo resources.
 */
public interface ICatalogGeoController {

  /**
   * Lists every catalog track.
   *
   * @return tracks
   */
  List<Track> tracks();

  /**
   * Creates a catalog track from the generated JOOQ POJO.
   *
   * @param track request body
   * @return stored track
   */
  Track createTrack(Track track);

  /**
   * Lists the royalty splits configured for one track.
   *
   * @param trackId track id
   * @return splits
   */
  List<RoyaltySplit> splits(Long trackId);

  /**
   * Lists royalty types.
   *
   * @return royalty types
   */
  List<RoyaltyType> royaltyTypes();

  /**
   * Lists channel pieces with their share splits.
   *
   * @return channel pieces
   */
  List<ChannelPiece> channelPieces();

  /**
   * Creates a channel piece from the generated JOOQ POJO.
   *
   * @param piece request body
   * @return stored channel piece
   */
  ChannelPiece createChannelPiece(ChannelPiece piece);

  /**
   * Lists geographic subscription tiers.
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
   * Lists geographic subscriptions.
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
   * Creates a tour plan from the generated JOOQ POJO.
   *
   * @param plan request body
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
   * Records a royalty deposit from the generated JOOQ POJO.
   *
   * @param deposit request body
   * @return stored deposit
   */
  RoyaltyDeposit depositRoyalty(RoyaltyDeposit deposit);
}
