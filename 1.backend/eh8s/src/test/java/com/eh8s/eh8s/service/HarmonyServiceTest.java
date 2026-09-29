package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMatchSuggestion;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.repository.interfaces.IHarmonyRepository;
import com.eh8s.eh8s.service.claude.ClaudeClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class HarmonyServiceTest {

  private static final Map<Long, String> INSTRUMENTS = Map.of(1L, "guitar", 2L, "voice", 3L, "drums");
  private static final Map<Long, Byte> LEVELS = Map.of(102L, (byte) 2, 104L, (byte) 4);

  private static MusicianProfile profile(long id, long account, long instrument, long level, String country, String genres) {
    MusicianProfile p = new MusicianProfile();
    p.setId(id);
    p.setAccountId(account);
    p.setInstrumentId(instrument);
    p.setEnigmaLevelId(level);
    p.setCountryCode(country);
    p.setGenres(genres);
    return p;
  }

  private static Band band() {
    Band b = new Band();
    b.setId(3L);
    b.setName("Smoke Band 30");
    b.setGeoCode("MEX_CDMX");
    b.setGenres("rock, blues");
    return b;
  }

  private final List<MusicianProfile> members = List.of(profile(1, 2, 1, 104, "MEX", "rock"));
  private final List<MusicianProfile> candidates =
      List.of(profile(2, 3, 2, 104, "MEX", "Rock, pop"), profile(3, 4, 1, 102, "USA", null));
  private final Map<String, BandMatchSuggestion> saved = new LinkedHashMap<>();
  private final AgentEventsFake events = new AgentEventsFake();

  private final IHarmonyRepository repo =
      new IHarmonyRepository() {
        @Override
        public Optional<Account> findAccount(Long accountId) {
          Account a = new Account();
          a.setId(accountId);
          a.setRole("musician");
          return Optional.of(a);
        }

        @Override
        public Optional<Long> findProfileIdByAccount(Long accountId) {
          return accountId == 2L ? Optional.of(1L) : Optional.of(99L);
        }

        @Override
        public Optional<Band> findBand(Long bandId) {
          return bandId == 3L ? Optional.of(band()) : Optional.empty();
        }

        @Override
        public List<MusicianProfile> findMembers(Long bandId) {
          return members;
        }

        @Override
        public List<MusicianProfile> findCandidates(Long bandId) {
          return candidates;
        }

        @Override
        public List<Instrument> findInstruments() {
          List<Instrument> out = new ArrayList<>();
          INSTRUMENTS.forEach(
              (id, code) -> {
                Instrument i = new Instrument();
                i.setId(id);
                i.setCode(code);
                out.add(i);
              });
          return out;
        }

        @Override
        public List<EnigmaLevel> findLevels() {
          List<EnigmaLevel> out = new ArrayList<>();
          LEVELS.forEach(
              (id, n) -> {
                EnigmaLevel l = new EnigmaLevel();
                l.setId(id);
                l.setLevelNumber(n);
                out.add(l);
              });
          return out;
        }

        @Override
        public void upsertSuggestion(BandMatchSuggestion s) {
          saved.put(s.getBandId() + "-" + s.getMusicianProfileId(), s);
        }

        @Override
        public void deleteMemberSuggestions(Long bandId) {}

        @Override
        public List<BandMatchSuggestion> findSuggestions(Long bandId) {
          List<BandMatchSuggestion> out = new ArrayList<>(saved.values());
          out.sort((a, b) -> b.getScore() - a.getScore());
          return out;
        }
      };

  private ClaudeClient claude(String key, String text) {
    return new ClaudeClient(key, "claude-sonnet-4-5", "http://127.0.0.1:9") {
      @Override
      public Map<String, Object> complete(String system, String user, int maxTokens) {
        Map<String, Object> out = new HashMap<>();
        out.put("text", text);
        out.put("model", "claude-sonnet-4-5");
        return out;
      }
    };
  }

  @Test
  void scoresInstrumentGapLevelCountryAndGenre() {
    BandMatchSuggestion voice = HarmonyService.score(band(), members, candidates.get(0), INSTRUMENTS, LEVELS);
    assertEquals((byte) 40, voice.getInstrumentPoints());
    assertEquals((byte) 30, voice.getLevelPoints());
    assertEquals((byte) 15, voice.getCountryPoints());
    assertEquals((byte) 8, voice.getGenrePoints(), "shares rock = 1 of 2 band genres");
    assertEquals((byte) 93, voice.getScore());
    assertTrue(voice.getReason().contains("fills the voice gap"));

    BandMatchSuggestion guitar = HarmonyService.score(band(), members, candidates.get(1), INSTRUMENTS, LEVELS);
    assertEquals((byte) 10, guitar.getInstrumentPoints());
    assertEquals((byte) 6, guitar.getLevelPoints(), "two levels below the band average");
    assertEquals((byte) 0, guitar.getCountryPoints());
    assertEquals((byte) 0, guitar.getGenrePoints());
    assertEquals((byte) 16, guitar.getScore());
    assertTrue(guitar.getReason().contains("USA outside MEX"));
  }

  @Test
  void runStoresRankingLogsEventAndAddsAiRationaleWhenKeySet() {
    HarmonyService service =
        new HarmonyService(repo, claude("k", "2: Fills voice at the same level.\nnoise\n3: Second guitar."), events);
    Map<String, Object> out = service.run(2L, 3L, true);

    assertEquals(Boolean.TRUE, out.get("aiUsed"));
    assertEquals("Fills voice at the same level.", saved.get("3-2").getAiRationale());
    assertEquals("Second guitar.", saved.get("3-3").getAiRationale());
    List<?> rows = (List<?>) out.get("suggestions");
    assertEquals(2, rows.size());
    assertEquals("HARMONY", events.codes.get(0));
    assertTrue(events.recorded.get(0).getSummary().contains("top: musician #2 (93)"));
  }

  @Test
  void withoutKeyStaysDeterministicAndStrangersAre403() {
    HarmonyService service = new HarmonyService(repo, claude("", "unused"), events);
    Map<String, Object> out = service.run(2L, 3L, true);
    assertEquals(Boolean.FALSE, out.get("aiUsed"));
    assertNull(saved.get("3-2").getAiRationale());
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.suggestions(7L, 3L))
            .getStatusCode()
            .value());
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.run(2L, 8L, false))
            .getStatusCode()
            .value());
  }
}
