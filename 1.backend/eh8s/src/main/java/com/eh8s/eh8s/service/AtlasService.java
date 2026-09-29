package com.eh8s.eh8s.service;

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
import com.eh8s.eh8s.repository.interfaces.IAtlasRepository;
import com.eh8s.eh8s.service.interfaces.IAgentEventService;
import com.eh8s.eh8s.service.interfaces.IAtlasService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * ATLAS nearest-neighbour tour routes with projected income from venue contract data.
 */
@Service
public class AtlasService implements IAtlasService {

  static final BigDecimal FILL_RATE = new BigDecimal("0.60");
  static final int DEFAULT_BAND_SHARE_BPS = 5000;
  static final int DEFAULT_MAX_STOPS = 6;
  static final int MAX_WINDOW_DAYS = 92;
  static final Set<String> TAKEN = Set.of("booked", "confirmed", "proposed", "played");

  private final IAtlasRepository atlasRepository;
  private final IAgentEventService agentEventService;
  private final Clock clock;

  /**
   * Creates the service with the system clock.
   *
   * @param atlasRepository route persistence
   * @param agentEventService agent event log
   */
  @Autowired
  public AtlasService(IAtlasRepository atlasRepository, IAgentEventService agentEventService) {
    this(atlasRepository, agentEventService, Clock.systemDefaultZone());
  }

  AtlasService(IAtlasRepository atlasRepository, IAgentEventService agentEventService, Clock clock) {
    this.atlasRepository = atlasRepository;
    this.agentEventService = agentEventService;
    this.clock = clock;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> generate(
      Long accountId, Long bandId, String windowStart, String windowEnd, Integer maxStopsInput) {
    Band band = requireBand(bandId);
    requireAllowed(accountId, bandId);
    LocalDate today = LocalDate.now(clock);
    LocalDate start = parseDate(windowStart, "windowStart");
    LocalDate end = parseDate(windowEnd, "windowEnd");
    if (start.isBefore(today)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowStart cannot be in the past");
    }
    if (end.isBefore(start)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowEnd must be on or after windowStart");
    }
    if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_WINDOW_DAYS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The window can span at most " + MAX_WINDOW_DAYS + " days");
    }
    int maxStops = maxStops(maxStopsInput);

    Map<Long, LocalDate> zoneExpiry = activeZoneExpiry(bandId, start);
    if (zoneExpiry.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "No active geographic subscription covers " + start + ". Subscribe the band to a zone first.");
    }
    Map<Long, GeoRegion> regions = new HashMap<>();
    for (GeoRegion r : atlasRepository.findRegions()) {
      regions.put(r.getId(), r);
    }
    Map<Long, Integer> shareBps = new HashMap<>();
    for (ContractType c : atlasRepository.findContractTypes()) {
      shareBps.put(c.getId(), c.getBandShareBps() == null ? DEFAULT_BAND_SHARE_BPS : c.getBandShareBps());
    }

    Map<Long, Long> venueZone = new HashMap<>();
    List<Venue> candidates = new ArrayList<>();
    for (Venue v : atlasRepository.findVenues()) {
      if (!approved(v)) {
        continue;
      }
      for (Long regionId : zoneExpiry.keySet()) {
        GeoRegion r = regions.get(regionId);
        if (r != null && inZone(r, v)) {
          venueZone.put(v.getId(), regionId);
          candidates.add(v);
          break;
        }
      }
    }
    if (candidates.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "No approved venue inside the band's subscribed zones");
    }

    Set<String> taken = new HashSet<>();
    for (Booking b : atlasRepository.findBookingsBetween(start, end)) {
      if (b.getStatus() != null && TAKEN.contains(b.getStatus())) {
        taken.add(b.getVenueId() + "|" + b.getShowDate());
      }
    }
    Map<Long, TreeSet<LocalDate>> openDays = new HashMap<>();
    for (VenueAvailability a : atlasRepository.findAvailabilityBetween(start, end)) {
      if (!taken.contains(a.getVenueId() + "|" + a.getAvailableDate())) {
        openDays.computeIfAbsent(a.getVenueId(), k -> new TreeSet<>()).add(a.getAvailableDate());
      }
    }

    List<Venue> route = route(candidates, v -> income(v, shareBps), maxStops);

    TourPlan plan = new TourPlan();
    plan.setBandId(bandId);
    plan.setGeoRegionId(venueZone.get(route.get(0).getId()));
    plan.setTitle("ATLAS route " + start + " to " + end);
    List<String> cities = new ArrayList<>();
    for (Venue v : route) {
      cities.add(v.getCity());
    }
    String note = String.join(" -> ", cities);
    plan.setRouteNote(note.length() > 400 ? note.substring(0, 400) : note);
    plan.setWindowStart(start);
    plan.setWindowEnd(end);
    plan.setGeneratedBy("atlas");
    plan.setCreatedAt(LocalDateTime.now(clock));

    List<TourPlanStop> stops = new ArrayList<>();
    BigDecimal totalKm = BigDecimal.ZERO;
    BigDecimal totalIncome = BigDecimal.ZERO;
    LocalDate lastDate = start.minusDays(1);
    Venue previous = null;
    byte order = 1;
    for (Venue v : route) {
      BigDecimal leg =
          previous == null
              ? BigDecimal.ZERO
              : BigDecimal.valueOf(km(previous, v)).setScale(1, RoundingMode.HALF_UP);
      BigDecimal income = income(v, shareBps);
      LocalDate cap = zoneExpiry.get(venueZone.get(v.getId()));
      LocalDate showDate = null;
      TreeSet<LocalDate> days = openDays.get(v.getId());
      if (days != null) {
        LocalDate next = days.higher(lastDate);
        if (next != null && !next.isAfter(cap) && !next.isAfter(end)) {
          showDate = next;
          lastDate = next;
        }
      }
      TourPlanStop stop = new TourPlanStop();
      stop.setStopOrder(order++);
      stop.setVenueId(v.getId());
      stop.setShowDate(showDate);
      stop.setLegKm(leg);
      stop.setProjectedIncomeUsdc(income);
      stops.add(stop);
      totalKm = totalKm.add(leg);
      totalIncome = totalIncome.add(income);
      previous = v;
    }
    plan.setTotalKm(totalKm);
    plan.setProjectedIncomeUsdc(totalIncome);
    TourPlan saved = atlasRepository.insertPlan(plan);
    for (TourPlanStop stop : stops) {
      stop.setTourPlanId(saved.getId());
      atlasRepository.insertStop(stop);
    }

    String summary =
        "ATLAS route for "
            + band.getName()
            + ": "
            + stops.size()
            + " stops, "
            + totalKm.toPlainString()
            + " km, "
            + totalIncome.toPlainString()
            + " USDC projected";
    agentEventService.record(
        "ATLAS", "tour_route", "green", summary, summary + ". " + saved.getRouteNote() + ". Plan #" + saved.getId() + ".");
    return planMap(saved);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> plans(Long accountId, Long bandId) {
    Band band = requireBand(bandId);
    requireAllowed(accountId, bandId);
    List<Map<String, Object>> plans = new ArrayList<>();
    for (TourPlan p : atlasRepository.findPlans(bandId)) {
      plans.add(planMap(p));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("band", band);
    out.put("activeZones", activeZones(bandId));
    out.put("plans", plans);
    return out;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> plan(Long accountId, Long planId) {
    TourPlan plan =
        atlasRepository
            .findPlan(planId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tour plan"));
    requireAllowed(accountId, plan.getBandId());
    return planMap(plan);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> activeZones(Long bandId) {
    Map<Long, GeoRegion> regions = new HashMap<>();
    for (GeoRegion r : atlasRepository.findRegions()) {
      regions.put(r.getId(), r);
    }
    List<String> out = new ArrayList<>();
    for (Map.Entry<Long, LocalDate> e : activeZoneExpiry(bandId, LocalDate.now(clock)).entrySet()) {
      GeoRegion r = regions.get(e.getKey());
      out.add((r == null ? "#" + e.getKey() : r.getCode()) + " until " + e.getValue());
    }
    return out;
  }

  /**
   * Nearest-neighbour route: start at the highest-income venue, then always hop to the closest
   * remaining one.
   *
   * @param venues candidate venues
   * @param income projected income per venue
   * @param maxStops stop cap
   * @return venues in route order
   */
  static List<Venue> route(
      List<Venue> venues, java.util.function.Function<Venue, BigDecimal> income, int maxStops) {
    List<Venue> remaining = new ArrayList<>(venues);
    remaining.sort(
        Comparator.comparing(income).reversed().thenComparing(Venue::getId));
    List<Venue> route = new ArrayList<>();
    Venue current = remaining.remove(0);
    route.add(current);
    while (!remaining.isEmpty() && route.size() < maxStops) {
      Venue from = current;
      Venue next =
          remaining.stream()
              .min(Comparator.comparingDouble((Venue v) -> km(from, v)).thenComparing(Venue::getId))
              .orElseThrow();
      remaining.remove(next);
      route.add(next);
      current = next;
    }
    return route;
  }

  /**
   * Projected band income at a venue: capacity x suggested ticket x fill rate x band share.
   *
   * @param v venue
   * @param shareBps contract type id to band share in basis points
   * @return USDC, 2 decimals
   */
  static BigDecimal income(Venue v, Map<Long, Integer> shareBps) {
    int bps = shareBps.getOrDefault(v.getContractTypeId(), DEFAULT_BAND_SHARE_BPS);
    BigDecimal ticket = v.getSuggestedTicketUsdc() == null ? BigDecimal.ZERO : v.getSuggestedTicketUsdc();
    return BigDecimal.valueOf(v.getCapacity() == null ? 0 : v.getCapacity())
        .multiply(ticket)
        .multiply(FILL_RATE)
        .multiply(BigDecimal.valueOf(bps))
        .divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
  }

  /**
   * Great-circle distance between two venues (haversine, Earth radius 6371 km).
   *
   * @param a first venue
   * @param b second venue
   * @return kilometres
   */
  static double km(Venue a, Venue b) {
    double lat1 = Math.toRadians(a.getLatitude().doubleValue());
    double lat2 = Math.toRadians(b.getLatitude().doubleValue());
    double dLat = lat2 - lat1;
    double dLon = Math.toRadians(b.getLongitude().doubleValue() - a.getLongitude().doubleValue());
    double h =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * 6371.0 * Math.asin(Math.sqrt(h));
  }

  static boolean inZone(GeoRegion r, Venue v) {
    if (r.getCountryCode() == null || !r.getCountryCode().equalsIgnoreCase(v.getCountryCode())) {
      return false;
    }
    return r.getCity() == null || r.getCity().equalsIgnoreCase(v.getCity());
  }

  private static boolean approved(Venue v) {
    return v.getListingStatus() == null || "approved".equals(v.getListingStatus());
  }

  private Map<Long, LocalDate> activeZoneExpiry(Long bandId, LocalDate on) {
    Map<Long, LocalDate> out = new LinkedHashMap<>();
    for (GeographicSubscription s : atlasRepository.findConfirmedSubscriptions(bandId)) {
      LocalDate expiry =
          s.getOnchainExpiresAt() != null ? s.getOnchainExpiresAt().toLocalDate() : s.getExpiresAt();
      if (expiry != null && !expiry.isBefore(on)) {
        out.merge(s.getGeoRegionId(), expiry, (x, y) -> x.isAfter(y) ? x : y);
      }
    }
    return out;
  }

  private Map<String, Object> planMap(TourPlan plan) {
    Map<Long, Venue> venues = new HashMap<>();
    for (Venue v : atlasRepository.findVenues()) {
      venues.put(v.getId(), v);
    }
    List<Map<String, Object>> stops = new ArrayList<>();
    for (TourPlanStop s : atlasRepository.findStops(plan.getId())) {
      Venue v = venues.get(s.getVenueId());
      Map<String, Object> venue = new LinkedHashMap<>();
      if (v != null) {
        venue.put("id", v.getId());
        venue.put("name", v.getName());
        venue.put("city", v.getCity());
        venue.put("latitude", v.getLatitude());
        venue.put("longitude", v.getLongitude());
        venue.put("capacity", v.getCapacity());
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("stop", s);
      row.put("venue", venue);
      stops.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("plan", plan);
    out.put("stops", stops);
    return out;
  }

  private static LocalDate parseDate(String raw, String field) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required (YYYY-MM-DD)");
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be YYYY-MM-DD");
    }
  }

  private static int maxStops(Integer n) {
    if (n == null) {
      return DEFAULT_MAX_STOPS;
    }
    if (n < 1 || n > 10) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxStops must be 1..10");
    }
    return n;
  }

  private Band requireBand(Long bandId) {
    return atlasRepository
        .findBand(bandId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown band"));
  }

  private void requireAllowed(Long accountId, Long bandId) {
    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    Account account =
        atlasRepository
            .findAccount(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
    if ("owner".equals(account.getRole())) {
      return;
    }
    Long profileId = atlasRepository.findProfileIdByAccount(accountId).orElse(null);
    if (profileId == null || !atlasRepository.isMember(bandId, profileId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only band members or the owner can plan tours for this band");
    }
  }
}
