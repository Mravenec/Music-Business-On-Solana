package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ContractType;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import com.eh8s.eh8s.repository.interfaces.IStageMapRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * pin colors from real data and Request slot rules.
 */
class StageMapServiceTest {

  private static final YearMonth OCT = YearMonth.of(2026, 10);

  private IStageMapRepository repo;
  private StageMapService service;

  @BeforeEach
  void setUp() {
    repo = mock(IStageMapRepository.class);
    Clock clock = Clock.fixed(LocalDate.of(2026, 9, 27).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    service = new StageMapService(repo, clock);
    Venue partner = venue(1L, (byte) 1, null);
    Venue booked = venue(2L, (byte) 0, "approved");
    Venue open = venue(3L, (byte) 0, null);
    Venue pending = venue(4L, (byte) 0, "pending");
    Venue empty = venue(5L, (byte) 0, "approved");
    List<Venue> venues = List.of(partner, booked, open, pending, empty);
    when(repo.findVenues()).thenReturn(venues);
    for (Venue v : venues) {
      when(repo.findVenue(v.getId())).thenReturn(Optional.of(v));
    }
    ContractType door = new ContractType();
    door.setId(1L);
    door.setName("Door deal");
    when(repo.findContractTypes()).thenReturn(List.of(door));
    when(repo.findAvailabilityBetween(any(), any()))
        .thenReturn(
            List.of(
                open(1L, 5), open(3L, 5), open(3L, 6), open(4L, 5), open(2L, 12)));
    when(repo.findBookingsBetween(any(), any()))
        .thenReturn(List.of(booking(2L, 9L, 10, "confirmed"), booking(3L, 9L, 6, "confirmed")));
  }

  @Test
  void pinsFollowPriorityPurpleYellowGreenRed() {
    List<Map<String, Object>> pins = service.pins(OCT);
    assertEquals("purple", pins.get(0).get("pin"));
    assertEquals("yellow", pins.get(1).get("pin"));
    assertEquals("yellow", pins.get(2).get("pin"));
    assertEquals("red", pins.get(3).get("pin"));
    assertEquals("red", pins.get(4).get("pin"));
    assertEquals("Door deal", pins.get(0).get("contractType"));
    assertEquals(List.of(LocalDate.of(2026, 10, 12)), pins.get(1).get("availableDays"));
    assertEquals(List.of(LocalDate.of(2026, 10, 5)), pins.get(2).get("availableDays"));
    assertEquals(List.of(), pins.get(3).get("availableDays"));
  }

  @Test
  void greenWhenApprovedWithOpenDatesAndNoConfirmedShow() {
    when(repo.findBookingsBetween(any(), any())).thenReturn(List.of());
    assertEquals("green", service.pin(3L, OCT).get("pin"));
    assertEquals(
        List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6)),
        service.pin(3L, OCT).get("availableDays"));
  }

  @Test
  void requestSlotCreatesRequestedBookingForTheMusiciansBand() {
    when(repo.findMusicianProfileId(20L)).thenReturn(Optional.of(1L));
    when(repo.findBandIds(1L)).thenReturn(List.of(4L));
    when(repo.insertBooking(any())).thenAnswer(inv -> inv.getArgument(0));

    Booking stored = service.requestSlot(20L, 3L, body(5, null));
    assertEquals("requested", stored.getStatus());
    assertEquals(4L, stored.getBandId());
    assertEquals(LocalDate.of(2026, 10, 5), stored.getShowDate());

    assertEquals(409, status(() -> service.requestSlot(20L, 3L, body(6, null))));
    assertEquals(409, status(() -> service.requestSlot(20L, 3L, body(7, null))));
    assertEquals(409, status(() -> service.requestSlot(20L, 4L, body(5, null))));
    assertEquals(403, status(() -> service.requestSlot(20L, 3L, body(5, 8L))));
    assertEquals(400, status(() -> service.requestSlot(20L, 3L, body(null, null))));

    when(repo.findBandIds(1L)).thenReturn(List.of(4L, 8L));
    assertEquals(400, status(() -> service.requestSlot(20L, 3L, body(5, null))));

    when(repo.bookingExists(3L, 4L, LocalDate.of(2026, 10, 5))).thenReturn(true);
    assertEquals(409, status(() -> service.requestSlot(20L, 3L, body(5, 4L))));

    when(repo.findMusicianProfileId(30L)).thenReturn(Optional.empty());
    assertEquals(403, status(() -> service.requestSlot(30L, 3L, body(5, null))));
  }

  @Test
  void addAvailabilityChecksDateAndVenueWallet() {
    Venue owned = venue(6L, (byte) 0, "approved");
    owned.setWalletPubkey("VenueWallet111");
    when(repo.findVenue(6L)).thenReturn(Optional.of(owned));
    when(repo.findAccountWallet(20L)).thenReturn(Optional.of("OtherWallet"));
    when(repo.findAccountWallet(21L)).thenReturn(Optional.of("VenueWallet111"));
    when(repo.insertAvailability(any())).thenAnswer(inv -> inv.getArgument(0));

    VenueAvailability past = new VenueAvailability();
    past.setAvailableDate(LocalDate.of(2026, 9, 1));
    assertEquals(400, status(() -> service.addAvailability(21L, 6L, past)));

    VenueAvailability next = new VenueAvailability();
    next.setAvailableDate(LocalDate.of(2026, 10, 20));
    assertEquals(403, status(() -> service.addAvailability(20L, 6L, next)));
    assertEquals(6L, service.addAvailability(21L, 6L, next).getVenueId());

    when(repo.availabilityExists(6L, LocalDate.of(2026, 10, 20))).thenReturn(true);
    assertEquals(409, status(() -> service.addAvailability(21L, 6L, next)));
  }

  private static int status(Runnable call) {
    return assertThrows(ResponseStatusException.class, call::run).getStatusCode().value();
  }

  private static Venue venue(Long id, byte partner, String listing) {
    Venue v = new Venue();
    v.setId(id);
    v.setName("Venue " + id);
    v.setIsPartner(partner);
    v.setListingStatus(listing);
    v.setContractTypeId(1L);
    return v;
  }

  private static VenueAvailability open(Long venueId, int day) {
    VenueAvailability a = new VenueAvailability();
    a.setVenueId(venueId);
    a.setAvailableDate(LocalDate.of(2026, 10, day));
    return a;
  }

  private static Booking booking(Long venueId, Long bandId, int day, String status) {
    Booking b = new Booking();
    b.setVenueId(venueId);
    b.setBandId(bandId);
    b.setShowDate(LocalDate.of(2026, 10, day));
    b.setStatus(status);
    return b;
  }

  private static Booking body(Integer day, Long bandId) {
    Booking b = new Booking();
    b.setShowDate(day == null ? null : LocalDate.of(2026, 10, day));
    b.setBandId(bandId);
    return b;
  }
}
