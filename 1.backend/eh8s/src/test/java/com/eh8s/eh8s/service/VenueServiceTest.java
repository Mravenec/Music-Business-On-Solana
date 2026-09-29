package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSettlement;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.PendingClaim;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Venue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Bar La Cueva settlement matches the EH8S brief.
 */
class VenueServiceTest {

  @Test
  void barLaCuevaPoolAndClaims() {
    ConcertSettlement settlement =
        VenueService.buildSettlement(1L, new BigDecimal("875.00"), new BigDecimal("200.00"));
    assertEquals(new BigDecimal("675.00"), settlement.getNetUsdc());
    assertEquals(new BigDecimal("101.25"), settlement.getEh8sFeeUsdc());
    assertEquals(new BigDecimal("573.75"), settlement.getBandPoolUsdc());

    List<PendingClaim> claims =
        VenueService.buildClaims(
            List.of(score(1L, 3800), score(3L, 3520), score(4L, 2000), score(5L, 680)),
            settlement.getBandPoolUsdc());
    assertEquals(new BigDecimal("218.03"), claims.get(0).getAmountUsdc());
    assertEquals(new BigDecimal("201.96"), claims.get(1).getAmountUsdc());
    assertEquals(new BigDecimal("114.75"), claims.get(2).getAmountUsdc());
    assertEquals(new BigDecimal("39.01"), claims.get(3).getAmountUsdc());
  }

  @Test
  void createVenueRejectsTheListingFormThatOmitsCapacityTicketAndContract() {
    VenueService service = new VenueService(null, null);
    Venue venue = new Venue();
    venue.setCode("CUEVA");
    venue.setName("Bar La Cueva");
    venue.setCity("CDMX");
    venue.setCountryCode("MEX");
    venue.setLatitude(new BigDecimal("19.4326"));
    venue.setLongitude(new BigDecimal("-99.1332"));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.createVenue(venue));
    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  void equalMemberClaimsWithoutSpp() {
    List<PendingClaim> claims =
        VenueService.buildEqualMemberClaims(List.of(9L), new BigDecimal("573.75"));
    assertEquals(1, claims.size());
    assertEquals(10000, claims.get(0).getShareBps());
    assertEquals(new BigDecimal("573.75"), claims.get(0).getAmountUsdc());
  }

  private static SppMemberScore score(Long musicianId, int bps) {
    SppMemberScore row = new SppMemberScore();
    row.setMusicianProfileId(musicianId);
    row.setShareBps(bps);
    return row;
  }
}
