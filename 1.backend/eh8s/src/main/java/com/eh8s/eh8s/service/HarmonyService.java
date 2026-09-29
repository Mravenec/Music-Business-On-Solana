package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IClaudeClient;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Band;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.BandMatchSuggestion;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Instrument;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.repository.interfaces.IHarmonyRepository;
import com.eh8s.eh8s.service.claude.ClaudeClient;
import com.eh8s.eh8s.service.interfaces.IAgentEventService;
import com.eh8s.eh8s.service.interfaces.IHarmonyService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * HARMONY deterministic band match with an optional Claude rationale.
 */
@Service
public class HarmonyService implements IHarmonyService {

  static final int INSTRUMENT_MAX = 40;
  static final int LEVEL_MAX = 30;
  static final int COUNTRY_MAX = 15;
  static final int GENRE_MAX = 15;
  static final int AI_TOP = 5;
  static final int MAX_TOKENS = 500;

  private static final Pattern RATIONALE_LINE = Pattern.compile("^\\s*#?(\\d+)\\s*[:\\-]\\s*(.+)$");

  static final String SYSTEM_PROMPT =
      "You are HARMONY, the band-matching agent of EH8S (Enigma H8 Studios). You receive a band and"
          + " candidate musicians with deterministic match points. For each candidate write one short"
          + " sentence (under 25 words) on why they fit or what to check, using only the data given."
          + " Reply with one line per candidate in the form '<musicianProfileId>: <sentence>' and"
          + " nothing else.";

  private final IHarmonyRepository harmonyRepository;
  private final IClaudeClient claudeClient;
  private final IAgentEventService agentEventService;

  /**
   * Creates the service.
   *
   * @param harmonyRepository suggestion persistence
   * @param claudeClient Anthropic Messages API client
   * @param agentEventService agent event log
   */
  public HarmonyService(
      IHarmonyRepository harmonyRepository,
      IClaudeClient claudeClient,
      IAgentEventService agentEventService) {
    this.harmonyRepository = harmonyRepository;
    this.claudeClient = claudeClient;
    this.agentEventService = agentEventService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> suggestions(Long accountId, Long bandId) {
    Band band = requireBand(bandId);
    requireAllowed(accountId, bandId);
    return payload(band, harmonyRepository.findSuggestions(bandId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> run(Long accountId, Long bandId, boolean withAi) {
    Band band = requireBand(bandId);
    requireAllowed(accountId, bandId);
    Map<Long, String> instruments = new HashMap<>();
    for (Instrument i : harmonyRepository.findInstruments()) {
      instruments.put(i.getId(), i.getCode());
    }
    Map<Long, Byte> levels = new HashMap<>();
    for (EnigmaLevel l : harmonyRepository.findLevels()) {
      levels.put(l.getId(), l.getLevelNumber());
    }
    List<MusicianProfile> members = harmonyRepository.findMembers(bandId);
    List<BandMatchSuggestion> ranked = new ArrayList<>();
    for (MusicianProfile candidate : harmonyRepository.findCandidates(bandId)) {
      ranked.add(score(band, members, candidate, instruments, levels));
    }
    ranked.sort(
        Comparator.comparing(BandMatchSuggestion::getScore)
            .reversed()
            .thenComparing(BandMatchSuggestion::getMusicianProfileId));

    boolean aiUsed = false;
    String aiError = null;
    if (withAi && claudeClient.isConfigured() && !ranked.isEmpty()) {
      try {
        List<BandMatchSuggestion> top = ranked.subList(0, Math.min(AI_TOP, ranked.size()));
        Map<String, Object> reply =
            claudeClient.complete(SYSTEM_PROMPT, buildPrompt(band, top), MAX_TOKENS);
        Map<Long, String> rationale = parseRationale(String.valueOf(reply.get("text")));
        for (BandMatchSuggestion s : top) {
          s.setAiRationale(rationale.get(s.getMusicianProfileId()));
        }
        aiUsed = !rationale.isEmpty();
      } catch (ResponseStatusException e) {
        aiError = e.getReason();
      }
    }
    for (BandMatchSuggestion s : ranked) {
      harmonyRepository.upsertSuggestion(s);
    }
    harmonyRepository.deleteMemberSuggestions(bandId);

    String summary =
        ranked.isEmpty()
            ? "HARMONY found no candidates for " + band.getName()
            : "HARMONY ranked "
                + ranked.size()
                + " candidates for "
                + band.getName()
                + "; top: musician #"
                + ranked.get(0).getMusicianProfileId()
                + " ("
                + ranked.get(0).getScore()
                + ")";
    agentEventService.record(
        "HARMONY",
        "band_match",
        "green",
        summary,
        ranked.isEmpty() ? summary : summary + ". " + ranked.get(0).getReason());

    Map<String, Object> out = payload(band, harmonyRepository.findSuggestions(bandId));
    out.put("aiUsed", aiUsed);
    out.put("aiError", aiError);
    return out;
  }

  /**
   * Scores one candidate against the band.
   *
   * <p>Instrument: 40 when nobody in the band plays it, 10 otherwise. Level: 30 minus 12 per
   * level away from the band average (15 for an empty band). Country: 15 when the candidate's
   * country matches the band zone prefix, 5 when unknown, 0 otherwise. Genre: 15 times the share
   * of band genres the candidate also plays (0 without genre data).
   *
   * @param band band
   * @param members current members
   * @param candidate candidate profile
   * @param instruments instrument id to code
   * @param levels enigma_level id to level number
   * @return unsaved suggestion with points and reason
   */
  static BandMatchSuggestion score(
      Band band,
      List<MusicianProfile> members,
      MusicianProfile candidate,
      Map<Long, String> instruments,
      Map<Long, Byte> levels) {
    List<String> reasons = new ArrayList<>();
    String instrument = instruments.getOrDefault(candidate.getInstrumentId(), "unknown");
    boolean gap = members.stream().noneMatch(m -> m.getInstrumentId().equals(candidate.getInstrumentId()));
    int instrumentPoints = gap ? INSTRUMENT_MAX : 10;
    reasons.add(gap ? "fills the " + instrument + " gap" : instrument + " already in the band");

    int candidateLevel = levels.getOrDefault(candidate.getEnigmaLevelId(), (byte) 0);
    int levelPoints;
    if (members.isEmpty()) {
      levelPoints = LEVEL_MAX / 2;
      reasons.add("level " + candidateLevel + " (empty band)");
    } else {
      double avg =
          members.stream().mapToInt(m -> levels.getOrDefault(m.getEnigmaLevelId(), (byte) 0)).average().orElse(0);
      double diff = Math.abs(candidateLevel - avg);
      levelPoints = (int) Math.max(0, Math.round(LEVEL_MAX - 12 * diff));
      reasons.add(String.format(Locale.ROOT, "level %d vs band avg %.1f", candidateLevel, avg));
    }

    String zone = band.getGeoCode() == null ? null : band.getGeoCode().trim().toUpperCase(Locale.ROOT);
    String bandCountry = zone == null || zone.length() < 3 ? null : zone.substring(0, 3);
    String country = candidate.getCountryCode();
    int countryPoints;
    if (country == null || country.isBlank() || bandCountry == null) {
      countryPoints = 5;
      reasons.add("country unknown");
    } else if (country.trim().equalsIgnoreCase(bandCountry)) {
      countryPoints = COUNTRY_MAX;
      reasons.add(country.trim().toUpperCase(Locale.ROOT) + " matches");
    } else {
      countryPoints = 0;
      reasons.add(country.trim().toUpperCase(Locale.ROOT) + " outside " + bandCountry);
    }

    Set<String> bandGenres = genres(band.getGenres());
    Set<String> candidateGenres = genres(candidate.getGenres());
    int genrePoints;
    if (bandGenres.isEmpty() || candidateGenres.isEmpty()) {
      genrePoints = 0;
      reasons.add("no genre data");
    } else {
      Set<String> shared = new LinkedHashSet<>(bandGenres);
      shared.retainAll(candidateGenres);
      genrePoints = (int) Math.round(GENRE_MAX * (double) shared.size() / bandGenres.size());
      reasons.add(shared.isEmpty() ? "no shared genres" : "shares " + String.join(", ", shared));
    }

    BandMatchSuggestion s = new BandMatchSuggestion();
    s.setBandId(band.getId());
    s.setMusicianProfileId(candidate.getId());
    s.setInstrumentPoints((byte) instrumentPoints);
    s.setLevelPoints((byte) levelPoints);
    s.setCountryPoints((byte) countryPoints);
    s.setGenrePoints((byte) genrePoints);
    s.setScore((byte) Math.min(100, instrumentPoints + levelPoints + countryPoints + genrePoints));
    String reason = "Musician #" + candidate.getId() + " " + String.join(" · ", reasons);
    s.setReason(reason.length() > 400 ? reason.substring(0, 400) : reason);
    return s;
  }

  static String buildPrompt(Band band, List<BandMatchSuggestion> top) {
    StringBuilder sb = new StringBuilder();
    sb.append("Band: ")
        .append(band.getName())
        .append(", zone ")
        .append(band.getGeoCode() == null ? "unknown" : band.getGeoCode())
        .append(", genres ")
        .append(band.getGenres() == null ? "unknown" : band.getGenres())
        .append(".\nCandidates (score out of 100 = instrument 40 + level 30 + country 15 + genre 15):\n");
    for (BandMatchSuggestion s : top) {
      sb.append(s.getMusicianProfileId())
          .append(": score ")
          .append(s.getScore())
          .append(" - ")
          .append(s.getReason())
          .append("\n");
    }
    return sb.toString();
  }

  static Map<Long, String> parseRationale(String text) {
    Map<Long, String> out = new HashMap<>();
    if (text == null) {
      return out;
    }
    for (String line : text.split("\\r?\\n")) {
      Matcher m = RATIONALE_LINE.matcher(line);
      if (m.matches()) {
        String sentence = m.group(2).trim();
        out.put(Long.parseLong(m.group(1)), sentence.length() > 1000 ? sentence.substring(0, 1000) : sentence);
      }
    }
    return out;
  }

  private Map<String, Object> payload(Band band, List<BandMatchSuggestion> suggestions) {
    Map<Long, String> instruments = new HashMap<>();
    for (Instrument i : harmonyRepository.findInstruments()) {
      instruments.put(i.getId(), i.getCode());
    }
    Map<Long, Byte> levels = new HashMap<>();
    for (EnigmaLevel l : harmonyRepository.findLevels()) {
      levels.put(l.getId(), l.getLevelNumber());
    }
    Map<Long, MusicianProfile> candidates = new HashMap<>();
    for (MusicianProfile p : harmonyRepository.findCandidates(band.getId())) {
      candidates.put(p.getId(), p);
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (BandMatchSuggestion s : suggestions) {
      MusicianProfile p = candidates.get(s.getMusicianProfileId());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("suggestion", s);
      row.put("instrument", p == null ? null : instruments.get(p.getInstrumentId()));
      row.put("level", p == null ? null : levels.get(p.getEnigmaLevelId()));
      row.put("countryCode", p == null ? null : p.getCountryCode());
      row.put("genres", p == null ? null : p.getGenres());
      rows.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("band", band);
    out.put("aiConfigured", claudeClient.isConfigured());
    out.put("suggestions", rows);
    return out;
  }

  private static Set<String> genres(String raw) {
    Set<String> out = new LinkedHashSet<>();
    if (raw == null) {
      return out;
    }
    for (String g : raw.split(",")) {
      String t = g.trim().toLowerCase(Locale.ROOT);
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  private Band requireBand(Long bandId) {
    return harmonyRepository
        .findBand(bandId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown band"));
  }

  private void requireAllowed(Long accountId, Long bandId) {
    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    Account account =
        harmonyRepository
            .findAccount(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
    if ("owner".equals(account.getRole())) {
      return;
    }
    Long profileId = harmonyRepository.findProfileIdByAccount(accountId).orElse(null);
    boolean member =
        profileId != null
            && harmonyRepository.findMembers(bandId).stream().anyMatch(m -> m.getId().equals(profileId));
    if (!member) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only band members or the owner can use HARMONY for this band");
    }
  }
}
