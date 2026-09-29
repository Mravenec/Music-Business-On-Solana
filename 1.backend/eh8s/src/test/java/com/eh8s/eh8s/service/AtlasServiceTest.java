package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AtlasServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(ZonedDateTime.of(2026, 9, 27, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

  private final List<GeographicSubscription> subs = new ArrayList<>();
  private final List<TourPlan> plans = new ArrayList<>();
  private final List<TourPlanStop> stops = new ArrayList<>();
  private final AgentEventsFake events = new AgentEventsFake();

  private static Venue venue(long id, String city, String country, double lat, double lon, int cap, String ticket, long contract, String listing) {
    Venue v = new Venue();
    v.setId(id);
    v.setName("Venue " + id);
    v.setCity(city);
    v.setCountryCode(country);
    v.setLatitude(BigDecimal.valueOf(lat));
    v.setLongitude(BigDecimal.valueOf(lon));
    v.setCapacity(cap);
    v.setSuggestedTicketUsdc(new BigDecimal(ticket));
    v.setContractTypeId(contract);
    v.setListingStatus(listing);
    return v;
  }

  private static final List<Venue> VENUES =
      List.of(
          venue(1, "Mexico City", "MEX", 19.432608, -99.133209, 180, "10.00", 3, null),
          venue(2, "Guadalajara", "MEX", 20.659698, -103.349609, 400, "12.00", 2, "approved"),
          venue(3, "Monterrey", "MEX", 25.6866, -100.3161, 250, "9.00", 1, null),
          venue(4, "Puebla", "MEX", 19.0414, -98.2063, 120, "8.00", 1, null),
          venue(5, "Leon", "MEX", 21.12, -101.68, 900, "20.00", 2, "pending"),
          venue(6, "Austin", "USA", 30.27, -97.74, 900, "20.00", 2, null));

  private final IAtlasRepository repo =
      new IAtlasRepository() {
        @Override
        public Optional<Account> findAccount(Long accountId) {
          Account a = new Account();
          a.setId(accountId);
          a.setRole(accountId == 1L ? "owner" : "musician");
          return Optional.of(a);
        }

        @Override
        public Optional<Long> findProfileIdByAccount(Long accountId) {
          return accountId == 2L ? Optional.of(1L) : Optional.empty();
        }

        @Override
        public Optional<Band> findBand(Long bandId) {
          Band b = new Band();
          b.setId(bandId);
          b.setName("Noche Oscura");
          return bandId == 1L ? Optional.of(b) : Optional.empty();
        }

        @Override
        public boolean isMember(Long bandId, Long musicianProfileId) {
          return musicianProfileId == 1L;
        }

        @Override
        public List<GeographicSubscription> findConfirmedSubscriptions(Long bandId) {
          return subs;
        }

        @Override
        public List<GeoRegion> findRegions() {
          GeoRegion mex = new GeoRegion();
          mex.setId(1L);
          mex.setCode("MEX");
          mex.setCountryCode("MEX");
          GeoRegion cdmx = new GeoRegion();
          cdmx.setId(2L);
          cdmx.setCode("MEX-CDMX");
          cdmx.setCountryCode("MEX");
          cdmx.setCity("Mexico City");
          return List.of(mex, cdmx);
        }

        @Override
        public List<Venue> findVenues() {
          return VENUES;
        }

        @Override
        public List<ContractType> findContractTypes() {
          List<ContractType> out = new ArrayList<>();
          int[] bps = {5000, 7500, 8000};
          for (int i = 0; i < bps.length; i++) {
            ContractType c = new ContractType();
            c.setId((long) i + 1);
            c.setBandShareBps(bps[i]);
            out.add(c);
          }
          return out;
        }

        @Override
        public List<VenueAvailability> findAvailabilityBetween(LocalDate from, LocalDate to) {
          List<VenueAvailability> out = new ArrayList<>();
          String[][] rows = {{"2", "2026-10-16"}, {"2", "2026-10-23"}, {"1", "2026-10-10"}, {"1", "2026-10-20"}, {"4", "2026-10-22"}};
          for (String[] r : rows) {
            VenueAvailability a = new VenueAvailability();
            a.setVenueId(Long.parseLong(r[0]));
            a.setAvailableDate(LocalDate.parse(r[1]));
            out.add(a);
          }
          return out;
        }

        @Override
        public List<Booking> findBookingsBetween(LocalDate from, LocalDate to) {
          Booking b = new Booking();
          b.setVenueId(4L);
          b.setShowDate(LocalDate.parse("2026-10-22"));
          b.setStatus("confirmed");
          return List.of(b);
        }

        @Override
        public TourPlan insertPlan(TourPlan plan) {
          plan.setId((long) plans.size() + 10);
          plans.add(plan);
          return plan;
        }

        @Override
        public TourPlanStop insertStop(TourPlanStop stop) {
          stop.setId((long) stops.size() + 1);
          stops.add(stop);
          return stop;
        }

        @Override
        public List<TourPlan> findPlans(Long bandId) {
          return plans;
        }

        @Override
        public Optional<TourPlan> findPlan(Long planId) {
          return plans.stream().filter(p -> p.getId().equals(planId)).findFirst();
        }

        @Override
        public List<TourPlanStop> findStops(Long planId) {
          return stops.stream().filter(s -> s.getTourPlanId().equals(planId)).toList();
        }
      };

  private void subscribe(long regionId, String expires) {
    GeographicSubscription s = new GeographicSubscription();
    s.setGeoRegionId(regionId);
    s.setExpiresAt(LocalDate.parse(expires));
    s.setOnChainStatus("confirmed");
    subs.add(s);
  }

  @Test
  void incomeUsesCapacityTicketFillAndContractShare() {
    Map<Long, Integer> share = Map.of(1L, 5000, 2L, 7500, 3L, 8000);
    assertEquals(new BigDecimal("2160.00"), AtlasService.income(VENUES.get(1), share));
    assertEquals(new BigDecimal("864.00"), AtlasService.income(VENUES.get(0), share));
    double km = AtlasService.km(VENUES.get(0), VENUES.get(1));
    assertTrue(km > 450 && km < 475, "CDMX to Guadalajara is about 460 km, got " + km);
  }

  @Test
  void countryZoneRoutesNearestNeighbourFromBestVenueWithOpenDates() {
    subscribe(1, "2026-12-31");
    AtlasService service = new AtlasService(repo, events, CLOCK);

    Map<String, Object> out = service.generate(2L, 1L, "2026-10-01", "2026-10-31", null);

    TourPlan plan = (TourPlan) out.get("plan");
    assertEquals("atlas", plan.getGeneratedBy());
    assertEquals("Guadalajara -> Mexico City -> Puebla -> Monterrey", plan.getRouteNote());
    assertEquals(List.of(2L, 1L, 4L, 3L), stops.stream().map(TourPlanStop::getVenueId).toList());
    assertEquals(LocalDate.parse("2026-10-16"), stops.get(0).getShowDate());
    assertEquals(LocalDate.parse("2026-10-20"), stops.get(1).getShowDate(), "first open day after the previous show");
    assertNull(stops.get(2).getShowDate(), "Puebla's only open day is already booked");
    assertEquals(new BigDecimal("0"), stops.get(0).getLegKm());
    assertEquals(new BigDecimal("3987.00"), plan.getProjectedIncomeUsdc());
    assertEquals("ATLAS", events.codes.get(0));
    assertTrue(events.recorded.get(0).getSummary().startsWith("ATLAS route for Noche Oscura: 4 stops"));
  }

  @Test
  void cityZoneKeepsOnlyThatCityAndExpiredOrMissingZonesAre409() {
    AtlasService service = new AtlasService(repo, events, CLOCK);
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> service.generate(2L, 1L, "2026-10-01", "2026-10-31", null))
            .getStatusCode()
            .value());
    subscribe(1, "2026-09-30");
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> service.generate(2L, 1L, "2026-10-01", "2026-10-31", null))
            .getStatusCode()
            .value());

    subscribe(2, "2026-10-15");
    service.generate(1L, 1L, "2026-10-01", "2026-10-31", null);
    assertEquals(List.of(1L), stops.stream().map(TourPlanStop::getVenueId).toList());
    assertEquals(LocalDate.parse("2026-10-10"), stops.get(0).getShowDate(), "10-20 is after the zone expiry");
  }

  @Test
  void rejectsBadWindowsAndStrangers() {
    subscribe(1, "2026-12-31");
    AtlasService service = new AtlasService(repo, events, CLOCK);
    int[] codes = {
      status(() -> service.generate(2L, 1L, "2026-09-01", "2026-09-30", null)),
      status(() -> service.generate(2L, 1L, "2026-10-10", "2026-10-01", null)),
      status(() -> service.generate(2L, 1L, "2026-10-01", "2027-02-01", null)),
      status(() -> service.generate(2L, 1L, "2026-10-01", "2026-10-31", 11)),
      status(() -> service.generate(3L, 1L, "2026-10-01", "2026-10-31", null)),
      status(() -> service.generate(2L, 9L, "2026-10-01", "2026-10-31", null))
    };
    assertEquals(List.of(400, 400, 400, 400, 403, 404), java.util.Arrays.stream(codes).boxed().toList());
  }

  private static int status(Runnable r) {
    return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
  }
}
