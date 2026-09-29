package com.eh8s.eh8s.repository.interfaces;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for ATLAS tour routes.
 */
public interface IAtlasRepository {

  /**
   * Loads an account.
   *
   * @param accountId account id
   * @return account when present
   */
  Optional<Account> findAccount(Long accountId);

  /**
   * Resolves the musician profile owned by an account.
   *
   * @param accountId account id
   * @return profile id when the account is a musician
   */
  Optional<Long> findProfileIdByAccount(Long accountId);

  /**
   * Loads a band.
   *
   * @param bandId band id
   * @return band when present
   */
  Optional<Band> findBand(Long bandId);

  /**
   * Whether a musician is in a band.
   *
   * @param bandId band id
   * @param musicianProfileId profile id
   * @return true for members
   */
  boolean isMember(Long bandId, Long musicianProfileId);

  /**
   * Geographic subscriptions of a band confirmed on-chain (expiry is checked by the service).
   *
   * @param bandId band id
   * @return confirmed subscriptions
   */
  List<GeographicSubscription> findConfirmedSubscriptions(Long bandId);

  /**
   * Geo regions (zones) with their venue filter.
   *
   * @return regions
   */
  List<GeoRegion> findRegions();

  /**
   * All venues.
   *
   * @return venues
   */
  List<Venue> findVenues();

  /**
   * Contract types with the band share used for projected income.
   *
   * @return contract types
   */
  List<ContractType> findContractTypes();

  /**
   * Venue open dates in a date range.
   *
   * @param from first day
   * @param to last day
   * @return availability rows
   */
  List<VenueAvailability> findAvailabilityBetween(LocalDate from, LocalDate to);

  /**
   * Bookings with a show date in a range.
   *
   * @param from first day
   * @param to last day
   * @return bookings
   */
  List<Booking> findBookingsBetween(LocalDate from, LocalDate to);

  /**
   * Inserts a tour plan.
   *
   * @param plan unsaved plan
   * @return stored plan with id
   */
  TourPlan insertPlan(TourPlan plan);

  /**
   * Inserts a tour stop.
   *
   * @param stop unsaved stop
   * @return stored stop with id
   */
  TourPlanStop insertStop(TourPlanStop stop);

  /**
   * Tour plans of a band, newest first.
   *
   * @param bandId band id
   * @return plans
   */
  List<TourPlan> findPlans(Long bandId);

  /**
   * Loads one tour plan.
   *
   * @param planId plan id
   * @return plan when present
   */
  Optional<TourPlan> findPlan(Long planId);

  /**
   * Stops of a plan in route order.
   *
   * @param planId plan id
   * @return stops
   */
  List<TourPlanStop> findStops(Long planId);
}
