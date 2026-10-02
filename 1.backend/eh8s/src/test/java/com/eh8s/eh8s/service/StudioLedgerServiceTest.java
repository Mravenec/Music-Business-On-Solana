package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.StudioPartner;
import com.eh8s.eh8s.service.StudioLedgerService.DatedFee;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A partner shares a fee only on calendar days inside their window.
 */
class StudioLedgerServiceTest {

  @Test
  void partnerAddedOnTheTwentiethMissesTheFifthAndSharesTheTwentyFifth() {
    StudioPartner early = partner(1L, 2500, LocalDateTime.of(2026, 10, 1, 9, 0), null);
    StudioPartner late = partner(2L, 2500, LocalDateTime.of(2026, 10, 20, 15, 0), null);
    DatedFee fifth = new DatedFee(LocalDateTime.of(2026, 10, 5, 12, 0), new BigDecimal("100.00"));
    DatedFee twentiethMorning =
        new DatedFee(LocalDateTime.of(2026, 10, 20, 9, 0), new BigDecimal("40.00"));
    DatedFee twentyFifth =
        new DatedFee(LocalDateTime.of(2026, 10, 25, 18, 0), new BigDecimal("100.00"));

    Map<Long, BigDecimal> beforeJoin = StudioLedgerService.shareOf(List.of(early, late), List.of(fifth));
    assertEquals(new BigDecimal("25.00"), beforeJoin.get(1L));
    assertNull(beforeJoin.get(2L));

    Map<Long, BigDecimal> joinDay =
        StudioLedgerService.shareOf(List.of(early, late), List.of(twentiethMorning));
    assertEquals(new BigDecimal("10.00"), joinDay.get(1L));
    assertEquals(new BigDecimal("10.00"), joinDay.get(2L));

    Map<Long, BigDecimal> afterJoin =
        StudioLedgerService.shareOf(List.of(early, late), List.of(twentyFifth));
    assertEquals(new BigDecimal("25.00"), afterJoin.get(1L));
    assertEquals(new BigDecimal("25.00"), afterJoin.get(2L));
  }

  @Test
  void partnerWhoLeftKeepsThatDayAndMissesTheNext() {
    StudioPartner left =
        partner(
            1L,
            2500,
            LocalDateTime.of(2026, 10, 1, 0, 0),
            LocalDateTime.of(2026, 10, 10, 16, 0));
    Map<Long, BigDecimal> lastDay =
        StudioLedgerService.shareOf(
            List.of(left),
            List.of(new DatedFee(LocalDateTime.of(2026, 10, 10, 9, 0), new BigDecimal("100.00"))));
    Map<Long, BigDecimal> nextDay =
        StudioLedgerService.shareOf(
            List.of(left),
            List.of(new DatedFee(LocalDateTime.of(2026, 10, 11, 9, 0), new BigDecimal("100.00"))));
    assertEquals(new BigDecimal("25.00"), lastDay.get(1L));
    assertFalse(nextDay.containsKey(1L));
  }

  private static StudioPartner partner(
      Long id, int shareBps, LocalDateTime startedAt, LocalDateTime endedAt) {
    return new StudioPartner(id, "wallet", "Partner", shareBps, (byte) 1, null, startedAt, endedAt);
  }
}
