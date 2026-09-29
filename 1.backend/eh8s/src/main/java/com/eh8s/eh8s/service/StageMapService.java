package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import com.eh8s.eh8s.repository.interfaces.IStageMapRepository;
import com.eh8s.eh8s.service.interfaces.IStageMapService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Derives Stage Map pin colors from venues, open dates, and bookings, and records slot requests.
 */
@Service
public class StageMapService implements IStageMapService {

  static final String PIN_PARTNER = "purple";
  static final String PIN_BOOKED = "yellow";
  static final String PIN_OPEN = "green";
  static final String PIN_CLOSED = "red";
  static final Set<String> CONFIRMED_STATUSES = Set.of("booked", "confirmed");
  static final Set<String> TAKEN_STATUSES = Set.of("booked", "confirmed", "proposed", "played");

  private final IStageMapRepository repository;
  private final Clock clock;

  /**
   * Creates the service with the system clock.
   *
   * @param repository Stage Map persistence
   */
  @Autowired
  public StageMapService(IStageMapRepository repository) {
    this(repository, Clock.systemDefaultZone());
  }

  /**
   * Creates the service with an explicit clock (tests).
   *
   * @param repository Stage Map persistence
   * @param clock clock that defines "today"
   */
  StageMapService(IStageMapRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Map<String, Object>> pins(YearMonth month) {
    YearMonth m = month == null ? YearMonth.now(clock) : month;
    MonthData data = load(m);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Venue venue : repository.findVenues()) {
      out.add(pinFor(venue, m, data));
    }
    return out;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> pin(Long venueId, YearMonth month) {
    YearMonth m = month == null ? YearMonth.now(clock) : month;
    Venue venue = venueOrThrow(venueId);
    return pinFor(venue, m, load(m));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public VenueAvailability addAvailability(Long accountId, Long venueId, VenueAvailability availability) {
    Venue venue = venueOrThrow(venueId);
    if (availability == null || availability.getAvailableDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "availableDate is required");
    }
    LocalDate date = availability.getAvailableDate();
    if (date.isBefore(LocalDate.now(clock))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick today or a later date");
    }
    if (venue.getWalletPubkey() != null && !venue.getWalletPubkey().isBlank()) {
      String wallet = accountId == null ? null : repository.findAccountWallet(accountId).orElse(null);
      if (!venue.getWalletPubkey().equals(wallet)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the venue wallet can publish dates");
      }
    }
    if (repository.availabilityExists(venueId, date)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "That date is already open");
    }
    VenueAvailability row = new VenueAvailability();
    row.setVenueId(venueId);
    row.setAvailableDate(date);
    return repository.insertAvailability(row);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Booking requestSlot(Long accountId, Long venueId, Booking booking) {
    Venue venue = venueOrThrow(venueId);
    if (booking == null || booking.getShowDate() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "showDate is required");
    }
    LocalDate date = booking.getShowDate();
    if (date.isBefore(LocalDate.now(clock))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick today or a later date");
    }
    if (!isApproved(venue)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Venue is not approved yet");
    }
    Long profile =
        accountId == null ? null : repository.findMusicianProfileId(accountId).orElse(null);
    if (profile == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only musicians can request a slot");
    }
    List<Long> bands = repository.findBandIds(profile);
    Long bandId = booking.getBandId();
    if (bandId != null) {
      if (!bands.contains(bandId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in that band");
      }
    } else if (bands.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Join a band first");
    } else if (bands.size() > 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick one of your bands (bandId)");
    } else {
      bandId = bands.get(0);
    }
    MonthData data = load(YearMonth.from(date));
    if (!openDays(venue, data).contains(date)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "That date is not open");
    }
    if (repository.bookingExists(venueId, bandId, date)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Your band already requested this date");
    }
    Booking row = new Booking();
    row.setVenueId(venueId);
    row.setBandId(bandId);
    row.setShowDate(date);
    row.setPipelineWeek((byte) 1);
    row.setStatus("requested");
    return repository.insertBooking(row);
  }

  private Map<String, Object> pinFor(Venue venue, YearMonth month, MonthData data) {
    List<LocalDate> open = openDays(venue, data);
    Booking nextShow = null;
    for (Booking b : data.bookings) {
      if (venue.getId().equals(b.getVenueId())
          && CONFIRMED_STATUSES.contains(b.getStatus())
          && !b.getShowDate().isBefore(data.today)) {
        nextShow = b;
        break;
      }
    }
    String pin;
    String label;
    if (venue.getIsPartner() != null && venue.getIsPartner() == 1) {
      pin = PIN_PARTNER;
      label = "EH8S partner venue";
    } else if (nextShow != null) {
      pin = PIN_BOOKED;
      label = "Upcoming confirmed show";
    } else if (isApproved(venue) && !open.isEmpty()) {
      pin = PIN_OPEN;
      label = "Open dates this month";
    } else {
      pin = PIN_CLOSED;
      label = "No availability this month";
    }
    ContractType contract = data.contractTypes.get(venue.getContractTypeId());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("venueId", venue.getId());
    out.put("venue", venue);
    out.put("pin", pin);
    out.put("pinLabel", label);
    out.put("contractType", contract == null ? null : contract.getName());
    out.put("availableDays", open);
    out.put("nextShow", nextShow);
    out.put("month", month.toString());
    return out;
  }

  private List<LocalDate> openDays(Venue venue, MonthData data) {
    List<LocalDate> out = new ArrayList<>();
    if (!isApproved(venue)) {
      return out;
    }
    for (VenueAvailability a : data.availability) {
      LocalDate day = a.getAvailableDate();
      if (!venue.getId().equals(a.getVenueId()) || day.isBefore(data.today)) {
        continue;
      }
      boolean taken =
          data.bookings.stream()
              .anyMatch(
                  b ->
                      venue.getId().equals(b.getVenueId())
                          && day.equals(b.getShowDate())
                          && TAKEN_STATUSES.contains(b.getStatus()));
      if (!taken) {
        out.add(day);
      }
    }
    return out;
  }

  /**
   * Listed venues must be approved by the STAGE agent; venues created before on-chain listing
   * (no listing status) are treated as approved.
   */
  private static boolean isApproved(Venue venue) {
    return venue.getListingStatus() == null || "approved".equals(venue.getListingStatus());
  }

  private Venue venueOrThrow(Long venueId) {
    return repository
        .findVenue(venueId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown venue"));
  }

  private MonthData load(YearMonth month) {
    LocalDate from = month.atDay(1);
    LocalDate to = month.plusMonths(1).atDay(1);
    Map<Long, ContractType> contracts = new HashMap<>();
    for (ContractType ct : repository.findContractTypes()) {
      contracts.put(ct.getId(), ct);
    }
    return new MonthData(
        LocalDate.now(clock),
        repository.findAvailabilityBetween(from, to),
        repository.findBookingsBetween(from, to),
        contracts);
  }

  private record MonthData(
      LocalDate today,
      List<VenueAvailability> availability,
      List<Booking> bookings,
      Map<Long, ContractType> contractTypes) {}
}
