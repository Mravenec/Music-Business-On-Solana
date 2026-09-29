package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.ChannelPiece;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoRegion;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoTier;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyDeposit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltyType;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.TourPlan;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.Track;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.ChannelPieceRecord;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.GeographicSubscriptionRecord;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.RoyaltyDepositRecord;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.TourPlanRecord;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.TrackRecord;
import com.eh8s.eh8s.repository.interfaces.ICatalogGeoRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.CHANNEL_PIECE;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEOGRAPHIC_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEO_REGION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEO_TIER;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.ROYALTY_DEPOSIT;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.ROYALTY_SPLIT;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.ROYALTY_TYPE;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.TOUR_PLAN;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.TRACK;

/**
 * JOOQ persistence for catalog and geo tables.
 */
@Repository
public class CatalogGeoRepository implements ICatalogGeoRepository {

  private final DSLContext dsl;

  /** @param dsl JOOQ context */
  public CatalogGeoRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<Track> findTracks() {
    return dsl.selectFrom(TRACK).orderBy(TRACK.ID).fetchInto(Track.class);
  }

  @Override
  public Track insertTrack(Track track) {
    TrackRecord rec = dsl.newRecord(TRACK, track);
    rec.changed(TRACK.ID, false);
    rec.store();
    return rec.into(Track.class);
  }

  @Override
  public List<RoyaltySplit> findSplits(Long trackId) {
    return dsl.selectFrom(ROYALTY_SPLIT)
        .where(ROYALTY_SPLIT.TRACK_ID.eq(trackId))
        .orderBy(ROYALTY_SPLIT.ID)
        .fetchInto(RoyaltySplit.class);
  }

  @Override
  public List<RoyaltyType> findRoyaltyTypes() {
    return dsl.selectFrom(ROYALTY_TYPE).orderBy(ROYALTY_TYPE.ID).fetchInto(RoyaltyType.class);
  }

  @Override
  public List<ChannelPiece> findChannelPieces() {
    return dsl.selectFrom(CHANNEL_PIECE).orderBy(CHANNEL_PIECE.ID).fetchInto(ChannelPiece.class);
  }

  @Override
  public ChannelPiece insertChannelPiece(ChannelPiece piece) {
    ChannelPieceRecord rec = dsl.newRecord(CHANNEL_PIECE, piece);
    rec.changed(CHANNEL_PIECE.ID, false);
    rec.store();
    return rec.into(ChannelPiece.class);
  }

  @Override
  public List<GeoTier> findGeoTiers() {
    return dsl.selectFrom(GEO_TIER).orderBy(GEO_TIER.ID).fetchInto(GeoTier.class);
  }

  @Override
  public Optional<GeoTier> findGeoTier(Long geoTierId) {
    return dsl.selectFrom(GEO_TIER).where(GEO_TIER.ID.eq(geoTierId)).fetchOptionalInto(GeoTier.class);
  }

  @Override
  public List<GeoRegion> findGeoRegions() {
    return dsl.selectFrom(GEO_REGION).orderBy(GEO_REGION.ID).fetchInto(GeoRegion.class);
  }

  @Override
  public Optional<GeoRegion> findGeoRegion(Long geoRegionId) {
    return dsl.selectFrom(GEO_REGION)
        .where(GEO_REGION.ID.eq(geoRegionId))
        .fetchOptionalInto(GeoRegion.class);
  }

  @Override
  public List<GeographicSubscription> findGeoSubscriptions() {
    return dsl.selectFrom(GEOGRAPHIC_SUBSCRIPTION)
        .orderBy(GEOGRAPHIC_SUBSCRIPTION.ID)
        .fetchInto(GeographicSubscription.class);
  }

  @Override
  public GeographicSubscription insertGeoSubscription(GeographicSubscription subscription) {
    GeographicSubscriptionRecord rec = dsl.newRecord(GEOGRAPHIC_SUBSCRIPTION, subscription);
    rec.changed(GEOGRAPHIC_SUBSCRIPTION.ID, false);
    rec.store();
    return rec.into(GeographicSubscription.class);
  }

  @Override
  public List<TourPlan> findTourPlans() {
    return dsl.selectFrom(TOUR_PLAN).orderBy(TOUR_PLAN.ID).fetchInto(TourPlan.class);
  }

  @Override
  public TourPlan insertTourPlan(TourPlan plan) {
    TourPlanRecord rec = dsl.newRecord(TOUR_PLAN, plan);
    rec.changed(TOUR_PLAN.ID, false);
    rec.store();
    return rec.into(TourPlan.class);
  }

  @Override
  public List<RoyaltyDeposit> findRoyaltyDeposits() {
    return dsl.selectFrom(ROYALTY_DEPOSIT).orderBy(ROYALTY_DEPOSIT.ID).fetchInto(RoyaltyDeposit.class);
  }

  @Override
  public RoyaltyDeposit insertRoyaltyDeposit(RoyaltyDeposit deposit) {
    RoyaltyDepositRecord rec = dsl.newRecord(ROYALTY_DEPOSIT, deposit);
    rec.changed(ROYALTY_DEPOSIT.ID, false);
    rec.store();
    return rec.into(RoyaltyDeposit.class);
  }
}
