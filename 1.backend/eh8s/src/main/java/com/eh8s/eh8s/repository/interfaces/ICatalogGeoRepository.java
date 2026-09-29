package com.eh8s.eh8s.repository.interfaces;

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
import java.util.Optional;

/**
 * Persistence for catalog, channel, and geographic subscription rows.
 */
public interface ICatalogGeoRepository {

  /** @param track row @return stored track */
  Track insertTrack(Track track);

  /** @return tracks */
  List<Track> findTracks();

  /** @param trackId track primary key @return royalty splits */
  List<RoyaltySplit> findSplits(Long trackId);

  /** @return royalty types */
  List<RoyaltyType> findRoyaltyTypes();

  /** @return channel pieces */
  List<ChannelPiece> findChannelPieces();

  /** @param piece channel piece to insert @return stored row */
  ChannelPiece insertChannelPiece(ChannelPiece piece);

  /** @return geo tiers */
  List<GeoTier> findGeoTiers();

  /** @param geoTierId geo_tier id @return tier (price + on-chain tier code) when present */
  Optional<GeoTier> findGeoTier(Long geoTierId);

  /** @return geo regions with their on-chain zone codes */
  List<GeoRegion> findGeoRegions();

  /** @param geoRegionId geo_region id @return region when present */
  Optional<GeoRegion> findGeoRegion(Long geoRegionId);

  /** @return geographic subscriptions */
  List<GeographicSubscription> findGeoSubscriptions();

  /** @param subscription row @return stored subscription */
  GeographicSubscription insertGeoSubscription(GeographicSubscription subscription);

  /** @return stored tour plans */
  List<TourPlan> findTourPlans();

  /** @param plan row @return stored plan */
  TourPlan insertTourPlan(TourPlan plan);

  /** @return royalty deposits */
  List<RoyaltyDeposit> findRoyaltyDeposits();

  /** @param deposit row @return stored deposit */
  RoyaltyDeposit insertRoyaltyDeposit(RoyaltyDeposit deposit);
}
