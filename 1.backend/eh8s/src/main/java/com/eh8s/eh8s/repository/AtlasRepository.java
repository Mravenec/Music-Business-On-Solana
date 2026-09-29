package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeoRegion;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.GeographicSubscription;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.TourPlan;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.TourPlanStop;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.TourPlanRecord;
import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.records.TourPlanStopRecord;
import com.eh8s.eh8s.repository.interfaces.IAtlasRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.eh8s.eh8s.database.jooq.eh8s.Tables.ACCOUNT;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BAND_MEMBER;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.BOOKING;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.CONTRACT_TYPE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.MUSICIAN_PROFILE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE;
import static com.eh8s.eh8s.database.jooq.eh8s.Tables.VENUE_AVAILABILITY;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEOGRAPHIC_SUBSCRIPTION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.GEO_REGION;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.TOUR_PLAN;
import static com.eh8s.eh8s.database.jooq.eh8s_catalog.Tables.TOUR_PLAN_STOP;

/**
 * JOOQ persistence for ATLAS tour routes.
 */
@Repository
public class AtlasRepository implements IAtlasRepository {

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public AtlasRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Account> findAccount(Long accountId) {
    return dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchOptionalInto(Account.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Long> findProfileIdByAccount(Long accountId) {
    return dsl.select(MUSICIAN_PROFILE.ID)
        .from(MUSICIAN_PROFILE)
        .where(MUSICIAN_PROFILE.ACCOUNT_ID.eq(accountId))
        .fetchOptional(MUSICIAN_PROFILE.ID);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<Band> findBand(Long bandId) {
    return dsl.selectFrom(BAND).where(BAND.ID.eq(bandId)).fetchOptionalInto(Band.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMember(Long bandId, Long musicianProfileId) {
    return dsl.fetchExists(
        BAND_MEMBER,
        BAND_MEMBER.BAND_ID.eq(bandId).and(BAND_MEMBER.MUSICIAN_PROFILE_ID.eq(musicianProfileId)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<GeographicSubscription> findConfirmedSubscriptions(Long bandId) {
    return dsl.selectFrom(GEOGRAPHIC_SUBSCRIPTION)
        .where(
            GEOGRAPHIC_SUBSCRIPTION
                .BAND_ID
                .eq(bandId)
                .and(GEOGRAPHIC_SUBSCRIPTION.ON_CHAIN_STATUS.eq("confirmed")))
        .orderBy(GEOGRAPHIC_SUBSCRIPTION.ID)
        .fetchInto(GeographicSubscription.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<GeoRegion> findRegions() {
    return dsl.selectFrom(GEO_REGION).orderBy(GEO_REGION.ID).fetchInto(GeoRegion.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Venue> findVenues() {
    return dsl.selectFrom(VENUE).orderBy(VENUE.ID).fetchInto(Venue.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ContractType> findContractTypes() {
    return dsl.selectFrom(CONTRACT_TYPE).orderBy(CONTRACT_TYPE.ID).fetchInto(ContractType.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<VenueAvailability> findAvailabilityBetween(LocalDate from, LocalDate to) {
    return dsl.selectFrom(VENUE_AVAILABILITY)
        .where(VENUE_AVAILABILITY.AVAILABLE_DATE.between(from, to))
        .orderBy(VENUE_AVAILABILITY.AVAILABLE_DATE)
        .fetchInto(VenueAvailability.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Booking> findBookingsBetween(LocalDate from, LocalDate to) {
    return dsl.selectFrom(BOOKING)
        .where(BOOKING.SHOW_DATE.between(from, to))
        .fetchInto(Booking.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TourPlan insertPlan(TourPlan plan) {
    TourPlanRecord rec = dsl.newRecord(TOUR_PLAN, plan);
    rec.changed(TOUR_PLAN.ID, false);
    rec.store();
    return rec.into(TourPlan.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TourPlanStop insertStop(TourPlanStop stop) {
    TourPlanStopRecord rec = dsl.newRecord(TOUR_PLAN_STOP, stop);
    rec.changed(TOUR_PLAN_STOP.ID, false);
    rec.store();
    return rec.into(TourPlanStop.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<TourPlan> findPlans(Long bandId) {
    return dsl.selectFrom(TOUR_PLAN)
        .where(TOUR_PLAN.BAND_ID.eq(bandId))
        .orderBy(TOUR_PLAN.ID.desc())
        .fetchInto(TourPlan.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<TourPlan> findPlan(Long planId) {
    return dsl.selectFrom(TOUR_PLAN).where(TOUR_PLAN.ID.eq(planId)).fetchOptionalInto(TourPlan.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<TourPlanStop> findStops(Long planId) {
    return dsl.selectFrom(TOUR_PLAN_STOP)
        .where(TOUR_PLAN_STOP.TOUR_PLAN_ID.eq(planId))
        .orderBy(TOUR_PLAN_STOP.STOP_ORDER)
        .fetchInto(TourPlanStop.class);
  }
}
