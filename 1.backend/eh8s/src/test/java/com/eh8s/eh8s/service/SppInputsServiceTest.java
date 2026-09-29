package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.repository.interfaces.ISppInputsRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * five SPP variables from real inputs, creative rating rules, concert minutes.
 */
class SppInputsServiceTest {

  private ISppInputsRepository repo;
  private SppInputsService service;

  @BeforeEach
  void setUp() {
    repo = mock(ISppInputsRepository.class);
    service = new SppInputsService(repo);
    when(repo.findVariables()).thenReturn(List.of());
  }

  @Test
  void closeComputesAllFiveVariablesWithManualWeights() {
    SppCycle cycle = cycle();
    when(repo.findSessionsBetween(eq(1L), any(), any()))
        .thenReturn(List.of(session(10L), session(11L)));
    when(repo.findCheckinsForSessions(anyList()))
        .thenReturn(
            List.of(checkin(10L, 1L, 10), checkin(11L, 1L, 10), checkin(10L, 2L, 0)));
    when(repo.findRatingsForSessions(anyList()))
        .thenReturn(List.of(rating(2L, 1L, 5), rating(3L, 1L, 4), rating(1L, 2L, 3)));
    Concert concert = new Concert();
    concert.setId(7L);
    concert.setBandId(1L);
    concert.setShowMinutes(60);
    when(repo.findConcertsByCycle(5L)).thenReturn(List.of(concert));
    when(repo.findSetCheckinsForConcerts(anyList()))
        .thenReturn(List.of(set(7L, 1L, 75), set(7L, 2L, 30)));
    when(repo.findEnigmaLevel(1L)).thenReturn(Optional.of(4));
    when(repo.findEnigmaLevel(2L)).thenReturn(Optional.of(4));

    SppMemberScore a = score(1L, 3);
    SppMemberScore b = score(2L, 4);
    List<SppMemberScore> scores = List.of(a, b);
    assertTrue(service.computeCycleScores(cycle, scores, LocalDate.of(2026, 9, 30)));

    assertEquals(400, a.getAttendancePoints());
    assertEquals(150, a.getPunctualityPoints());
    assertEquals(180, a.getCreativePoints());
    assertEquals(150, a.getSkillPoints());
    assertEquals(100, a.getConcertPoints());
    assertEquals(980, a.getTotalPoints());
    assertEquals((byte) 4, a.getEnigmaLevelEnd());

    assertEquals(200, b.getAttendancePoints());
    assertEquals(0, b.getPunctualityPoints());
    assertEquals(120, b.getCreativePoints());
    assertEquals(0, b.getSkillPoints());
    assertEquals(50, b.getConcertPoints());
    assertEquals(370, b.getTotalPoints());

    BandService.applyShareBps(new java.util.ArrayList<>(scores));
    assertEquals(7259, a.getShareBps());
    assertEquals(2741, b.getShareBps());
  }

  @Test
  void legacyCycleWithoutInputsKeepsStoredPoints() {
    when(repo.findSessionsBetween(anyLong(), any(), any())).thenReturn(List.of());
    when(repo.findConcertsByCycle(5L)).thenReturn(List.of());
    SppMemberScore seeded = score(1L, null);
    seeded.setTotalPoints(95);
    assertFalse(service.computeCycleScores(cycle(), List.of(seeded), LocalDate.of(2026, 6, 30)));
    assertEquals(95, seeded.getTotalPoints());
    assertEquals(0, seeded.getAttendancePoints());
  }

  @Test
  void rateUsesJwtMusicianAndRejectsBadInput() {
    when(repo.findMusicianProfileId(20L)).thenReturn(Optional.of(1L));
    when(repo.findSession(10L)).thenReturn(Optional.of(session(10L)));
    when(repo.isMember(1L, 1L)).thenReturn(true);
    when(repo.isMember(1L, 2L)).thenReturn(true);
    when(repo.findCheckin(10L, 1L)).thenReturn(Optional.of(checkin(10L, 1L, 10)));
    when(repo.insertRating(any())).thenAnswer(inv -> inv.getArgument(0));

    CreativeRating body = new CreativeRating();
    body.setRaterProfileId(99L);
    body.setRateeProfileId(2L);
    body.setScore((byte) 4);
    CreativeRating stored = service.rate(20L, 10L, body);
    assertEquals(1L, stored.getRaterProfileId());
    assertEquals((byte) 4, stored.getScore());

    body.setScore((byte) 6);
    assertEquals(400, status(() -> service.rate(20L, 10L, body)));
    body.setScore((byte) 3);
    body.setRateeProfileId(1L);
    assertEquals(400, status(() -> service.rate(20L, 10L, body)));

    body.setRateeProfileId(2L);
    when(repo.ratingExists(10L, 1L, 2L)).thenReturn(true);
    assertEquals(409, status(() -> service.rate(20L, 10L, body)));

    when(repo.findCheckin(10L, 1L)).thenReturn(Optional.empty());
    assertEquals(409, status(() -> service.rate(20L, 10L, body)));

    when(repo.findMusicianProfileId(30L)).thenReturn(Optional.empty());
    assertEquals(403, status(() -> service.rate(30L, 10L, body)));
  }

  @Test
  void endSetCapsMinutesAtShowLengthAndNeedsStart() {
    when(repo.findMusicianProfileId(20L)).thenReturn(Optional.of(1L));
    Concert concert = new Concert();
    concert.setId(7L);
    concert.setBandId(1L);
    concert.setShowMinutes(45);
    when(repo.findConcert(7L)).thenReturn(Optional.of(concert));
    when(repo.isMember(1L, 1L)).thenReturn(true);
    when(repo.findSetCheckin(7L, 1L)).thenReturn(Optional.empty());
    assertEquals(409, status(() -> service.endSet(20L, 7L, null)));
    verify(repo, never()).updateSetCheckinEnd(any());

    ConcertSetCheckin started = new ConcertSetCheckin();
    started.setId(3L);
    started.setConcertId(7L);
    started.setMusicianProfileId(1L);
    started.setSetStartedAt(LocalDateTime.of(2026, 9, 27, 21, 0));
    when(repo.findSetCheckin(7L, 1L)).thenReturn(Optional.of(started));
    when(repo.updateSetCheckinEnd(any())).thenAnswer(inv -> inv.getArgument(0));
    ConcertSetCheckin end = new ConcertSetCheckin();
    end.setSetEndedAt(LocalDateTime.of(2026, 9, 27, 22, 10));
    assertEquals(45, service.endSet(20L, 7L, end).getMinutesPlayed());
    assertEquals(409, status(() -> service.endSet(20L, 7L, end)));
    assertEquals(409, status(() -> service.startSet(20L, 7L, null)));
  }

  private static int status(Runnable call) {
    return assertThrows(ResponseStatusException.class, call::run).getStatusCode().value();
  }

  private static SppCycle cycle() {
    SppCycle c = new SppCycle();
    c.setId(5L);
    c.setBandId(1L);
    c.setStatus("open");
    c.setStartedAt(LocalDate.of(2026, 9, 1));
    return c;
  }

  private static RehearsalSession session(Long id) {
    RehearsalSession s = new RehearsalSession();
    s.setId(id);
    s.setBandId(1L);
    s.setScheduledAt(LocalDateTime.of(2026, 9, 10, 18, 0));
    return s;
  }

  private static RehearsalCheckin checkin(Long sessionId, Long profile, int punctuality) {
    RehearsalCheckin c = new RehearsalCheckin();
    c.setRehearsalSessionId(sessionId);
    c.setMusicianProfileId(profile);
    c.setAttendancePoints(10);
    c.setPunctualityPoints(punctuality);
    return c;
  }

  private static CreativeRating rating(Long rater, Long ratee, int score) {
    CreativeRating r = new CreativeRating();
    r.setRaterProfileId(rater);
    r.setRateeProfileId(ratee);
    r.setScore((byte) score);
    return r;
  }

  private static ConcertSetCheckin set(Long concertId, Long profile, int minutes) {
    ConcertSetCheckin s = new ConcertSetCheckin();
    s.setConcertId(concertId);
    s.setMusicianProfileId(profile);
    s.setMinutesPlayed(minutes);
    return s;
  }

  private static SppMemberScore score(Long profile, Integer levelStart) {
    SppMemberScore s = new SppMemberScore();
    s.setMusicianProfileId(profile);
    s.setAttendancePoints(0);
    s.setPunctualityPoints(0);
    s.setCreativePoints(0);
    s.setSkillPoints(0);
    s.setConcertPoints(0);
    s.setTotalPoints(0);
    s.setShareBps(0);
    s.setEnigmaLevelStart(levelStart == null ? null : levelStart.byteValue());
    return s;
  }
}
