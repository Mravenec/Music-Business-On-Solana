package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IClaudeClient;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.repository.interfaces.INexusRepository;
import com.eh8s.eh8s.service.claude.ClaudeClient;
import com.eh8s.eh8s.service.interfaces.IAgentEventService;
import com.eh8s.eh8s.service.interfaces.INexusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * NEXUS Score Enigma through Claude. Claude never hears audio: it scores the teacher rubric, the
 * notes and the recording link metadata, and the prompt says so.
 */
@Service
public class NexusService implements INexusService {

  /** Rubric dimensions, each scored 0..10 by the teacher or musician. */
  public static final List<String> RUBRIC_KEYS =
      List.of("technique", "timing", "tone", "expression", "theory");

  static final int MAX_TOKENS = 700;

  static final String AUDIO_NOTE =
      "NEXUS reads the rubric, notes and recording link only. Claude cannot listen to audio.";

  private final INexusRepository nexusRepository;
  private final IClaudeClient claudeClient;
  private final IAgentEventService agentEventService;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the service.
   *
   * @param nexusRepository evaluation persistence
   * @param claudeClient Anthropic Messages API client
   * @param agentEventService agent event log
   */
  public NexusService(
      INexusRepository nexusRepository,
      IClaudeClient claudeClient,
      IAgentEventService agentEventService) {
    this.nexusRepository = nexusRepository;
    this.claudeClient = claudeClient;
    this.agentEventService = agentEventService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("aiConfigured", claudeClient.isConfigured());
    out.put("model", claudeClient.model());
    out.put("rubricKeys", RUBRIC_KEYS);
    out.put("levels", nexusRepository.findLevels());
    out.put("audioNote", AUDIO_NOTE);
    return out;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> evaluate(Long accountId, EnigmaEvaluation input) {
    Account account = requireAccount(accountId);
    if (input == null || input.getMusicianProfileId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "musicianProfileId is required");
    }
    Long profileId = input.getMusicianProfileId();
    MusicianProfile profile = requireProfile(profileId);
    requireAllowed(account, profile);

    Map<String, Integer> rubric = parseRubricJson(input.getRubricJson());
    String recordingUrl = parseUrl(input.getRecordingUrl());
    String notes = input.getNotes() == null ? null : input.getNotes().trim();
    if (notes != null && notes.length() > 500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notes must be 500 characters or less");
    }
    Lesson lesson = input.getLessonId() == null ? null : requirePracticeLesson(input.getLessonId());
    if (!claudeClient.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "NEXUS unavailable: ANTHROPIC_API_KEY not set");
    }

    List<EnigmaLevel> levels = nexusRepository.findLevels();
    byte currentLevel = levelNumber(levels, profile.getEnigmaLevelId());
    String instrument = nexusRepository.findInstrumentCode(profile.getInstrumentId()).orElse("unknown");
    Map<String, Object> reply =
        claudeClient.complete(
            systemPrompt(levels),
            buildPrompt(profileId, instrument, currentLevel, rubric, notes, recordingUrl, practice(lesson)),
            MAX_TOKENS);
    Map<String, Object> parsed = parseReply(String.valueOf(reply.get("text")), currentLevel);

    EnigmaEvaluation row = new EnigmaEvaluation();
    row.setMusicianProfileId(profileId);
    row.setLessonId(lesson == null ? null : lesson.getId());
    row.setEvaluatorAccountId(account.getId());
    row.setWeekStart(LocalDate.now().with(DayOfWeek.MONDAY));
    row.setScore((Byte) parsed.get("score"));
    row.setNotes(notes == null || notes.isEmpty() ? null : notes);
    row.setRecordingUrl(recordingUrl);
    row.setRubricJson(toJson(rubric));
    row.setStrengths(joinLines(parsed.get("strengths")));
    row.setErrorsFound(joinLines(parsed.get("errors")));
    row.setExercises(joinLines(parsed.get("exercises")));
    row.setRecommendedLevel((Byte) parsed.get("recommendedLevel"));
    row.setSource("nexus_ai");
    row.setAiModel(String.valueOf(reply.get("model")));
    row.setCreatedAt(LocalDateTime.now());
    EnigmaEvaluation saved = nexusRepository.insertEvaluation(row);
    nexusRepository.updateEnigmaScore(profileId, saved.getScore());

    boolean levelChange = saved.getRecommendedLevel() != currentLevel;
    String summary =
        "Score Enigma "
            + saved.getScore()
            + " for musician #"
            + profileId
            + " ("
            + instrument
            + ")"
            + (levelChange
                ? ": recommends level " + saved.getRecommendedLevel() + " (now " + currentLevel + ")"
                : ": stays at level " + currentLevel);
    agentEventService.record(
        "NEXUS",
        "score_enigma",
        levelChange ? "yellow" : "green",
        summary,
        summary + ". " + String.valueOf(parsed.get("summary")) + " Evaluation #" + saved.getId() + ".");

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("evaluation", saved);
    out.put("currentLevel", currentLevel);
    out.put("recommendedLevel", saved.getRecommendedLevel());
    out.put("levelChange", levelChange);
    out.put("summary", parsed.get("summary"));
    out.put("audioNote", AUDIO_NOTE);
    return out;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Map<String, Object>> evaluations(Long accountId, Long musicianProfileId) {
    Account account = requireAccount(accountId);
    Long profileId = musicianProfileId;
    if (profileId == null) {
      profileId =
          nexusRepository
              .findProfileIdByAccount(account.getId())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "musicianProfileId is required"));
    }
    MusicianProfile profile = requireProfile(profileId);
    requireAllowed(account, profile);
    List<Map<String, Object>> out = new ArrayList<>();
    for (EnigmaEvaluation e : nexusRepository.findEvaluations(profileId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("evaluation", refreshApplied(e));
      out.add(row);
    }
    return out;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, Object> evaluation(Long accountId, Long evaluationId) {
    Account account = requireAccount(accountId);
    EnigmaEvaluation e =
        nexusRepository
            .findEvaluation(evaluationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown evaluation"));
    MusicianProfile profile = requireProfile(e.getMusicianProfileId());
    requireAllowed(account, profile);
    byte currentLevel = levelNumber(nexusRepository.findLevels(), profile.getEnigmaLevelId());
    Map<String, Object> musician = new LinkedHashMap<>();
    musician.put("id", profile.getId());
    musician.put("instrument", nexusRepository.findInstrumentCode(profile.getInstrumentId()).orElse(null));
    musician.put("enigmaScore", profile.getEnigmaScore());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("evaluation", refreshApplied(e));
    out.put("musician", musician);
    out.put("currentLevel", currentLevel);
    out.put(
        "levelChange", e.getRecommendedLevel() != null && e.getRecommendedLevel() != currentLevel);
    out.put("audioNote", AUDIO_NOTE);
    return out;
  }

  /**
   * System prompt: NEXUS role, the Enigma ladder, the no-audio rule and the JSON reply contract.
   *
   * @param levels Enigma levels 0..5
   * @return system prompt
   */
  static String systemPrompt(List<EnigmaLevel> levels) {
    StringBuilder ladder = new StringBuilder();
    for (EnigmaLevel l : levels) {
      ladder
          .append("- Level ")
          .append(l.getLevelNumber())
          .append(" ")
          .append(l.getName())
          .append(": ")
          .append(l.getMilestone())
          .append("\n");
    }
    return "You are NEXUS, the pedagogical agent of EH8S (Enigma H8 Studios), a music academy."
        + " You turn a teacher rubric into a Score Enigma from 0 to 100 and a level recommendation."
        + "\nEnigma levels:\n"
        + ladder
        + "You cannot hear audio. You only receive rubric scores (0..10 per dimension), teacher"
        + " notes and a recording link you cannot open; never claim you listened."
        + " Recommend at most one level up or down from the current level, and only when the rubric"
        + " clearly supports it; otherwise keep the current level."
        + " Reply with ONLY one JSON object, no prose: {\"score\": 0-100, \"strengths\": [up to 3"
        + " short strings], \"errors\": [up to 3], \"exercises\": [up to 3 concrete exercises],"
        + " \"recommendedLevel\": 0-5, \"summary\": \"one sentence\"}.";
  }

  /**
   * User message: musician context, rubric, notes and recording metadata.
   *
   * @param profileId musician profile id
   * @param instrument instrument code
   * @param currentLevel current Enigma level
   * @param rubric rubric scores
   * @param notes teacher notes (nullable)
   * @param recordingUrl recording link (nullable)
   * @return prompt text
   */
  static String buildPrompt(
      Long profileId,
      String instrument,
      byte currentLevel,
      Map<String, Integer> rubric,
      String notes,
      String recordingUrl) {
    return buildPrompt(profileId, instrument, currentLevel, rubric, notes, recordingUrl, null);
  }

  /**
   * User message with the academy lesson exercise the recording answers.
   *
   * @param profileId musician profile id
   * @param instrument instrument code
   * @param currentLevel current Enigma level
   * @param rubric rubric scores
   * @param notes teacher notes (nullable)
   * @param recordingUrl recording link (nullable)
   * @param practice lesson title and practice prompt (nullable)
   * @return prompt text
   */
  static String buildPrompt(
      Long profileId,
      String instrument,
      byte currentLevel,
      Map<String, Integer> rubric,
      String notes,
      String recordingUrl,
      String practice) {
    StringBuilder sb = new StringBuilder();
    sb.append("Musician #")
        .append(profileId)
        .append(", instrument ")
        .append(instrument)
        .append(", current Enigma level ")
        .append(currentLevel)
        .append(".\nRubric (0-10): ");
    rubric.forEach((k, v) -> sb.append(k).append(" ").append(v).append("; "));
    sb.append("\nNotes: ").append(notes == null || notes.isEmpty() ? "(none)" : notes);
    sb.append("\nRecording link (not listened to): ")
        .append(recordingUrl == null ? "(none)" : recordingUrl);
    if (practice != null) {
      sb.append("\nPractice exercise being answered: ").append(practice);
    }
    sb.append("\nReturn the JSON object now.");
    return sb.toString();
  }

  /**
   * Parses Claude's JSON reply. The score is clamped to 0..100 and the recommended level to one
   * step from the current level.
   *
   * @param text reply text (may wrap the JSON in prose or a code fence)
   * @param currentLevel current Enigma level
   * @return map with score (Byte), strengths/errors/exercises (List of String),
   *     recommendedLevel (Byte), summary
   * @throws ResponseStatusException 502 when no JSON object with a numeric score is found
   */
  Map<String, Object> parseReply(String text, byte currentLevel) {
    int start = text == null ? -1 : text.indexOf('{');
    int end = text == null ? -1 : text.lastIndexOf('}');
    if (start < 0 || end <= start) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NEXUS reply had no JSON score");
    }
    JsonNode root;
    try {
      root = mapper.readTree(text.substring(start, end + 1));
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NEXUS reply had no JSON score");
    }
    if (!root.path("score").isNumber()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NEXUS reply had no JSON score");
    }
    int score = Math.max(0, Math.min(100, (int) Math.round(root.path("score").asDouble())));
    int level = root.path("recommendedLevel").isNumber() ? root.path("recommendedLevel").asInt() : currentLevel;
    level = Math.max(Math.max(0, currentLevel - 1), Math.min(Math.min(5, currentLevel + 1), level));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("score", (byte) score);
    out.put("strengths", strings(root.path("strengths")));
    out.put("errors", strings(root.path("errors")));
    out.put("exercises", strings(root.path("exercises")));
    out.put("recommendedLevel", (byte) level);
    out.put("summary", root.path("summary").asText(""));
    return out;
  }

  private static List<String> strings(JsonNode node) {
    List<String> out = new ArrayList<>();
    if (node.isArray()) {
      for (JsonNode n : node) {
        String s = n.asText("").trim();
        if (!s.isEmpty() && out.size() < 5) {
          out.add(s);
        }
      }
    } else if (node.isTextual() && !node.asText().isBlank()) {
      out.add(node.asText().trim());
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static String joinLines(Object list) {
    String joined = String.join("\n", (List<String>) list);
    if (joined.isEmpty()) {
      return null;
    }
    return joined.length() > 1000 ? joined.substring(0, 1000) : joined;
  }

  Map<String, Integer> parseRubricJson(String rubricJson) {
    if (rubricJson == null || rubricJson.isBlank()) {
      return parseRubric(null);
    }
    try {
      return parseRubric(mapper.readValue(rubricJson, Map.class));
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "rubricJson must be a JSON object: " + String.join(", ", RUBRIC_KEYS) + " (0..10)");
    }
  }

  static Map<String, Integer> parseRubric(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "rubric is required: " + String.join(", ", RUBRIC_KEYS) + " (0..10)");
    }
    Map<String, Integer> out = new LinkedHashMap<>();
    for (String key : RUBRIC_KEYS) {
      Object v = map.get(key);
      int n;
      try {
        n = v instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(v).trim());
      } catch (NumberFormatException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rubric." + key + " must be 0..10");
      }
      if (n < 0 || n > 10) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rubric." + key + " must be 0..10");
      }
      out.put(key, n);
    }
    return out;
  }

  private static String parseUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String url = raw.trim();
    if (!(url.startsWith("https://") || url.startsWith("http://")) || url.length() > 400) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "recordingUrl must be an http(s) link of 400 characters or less");
    }
    return url;
  }

  private EnigmaEvaluation refreshApplied(EnigmaEvaluation e) {
    if (e.getAppliedAt() == null && e.getRecommendedLevel() != null && e.getCreatedAt() != null) {
      nexusRepository
          .findLevelChangeSince(e.getMusicianProfileId(), e.getRecommendedLevel(), e.getCreatedAt())
          .ifPresent(
              change -> {
                nexusRepository.markApplied(e.getId(), change.getCreatedAt());
                e.setAppliedAt(change.getCreatedAt());
              });
    }
    return e;
  }

  private static byte levelNumber(List<EnigmaLevel> levels, Long levelId) {
    for (EnigmaLevel l : levels) {
      if (l.getId().equals(levelId)) {
        return l.getLevelNumber();
      }
    }
    return 0;
  }

  private Account requireAccount(Long accountId) {
    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    return nexusRepository
        .findAccount(accountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
  }

  private Lesson requirePracticeLesson(Long lessonId) {
    return nexusRepository
        .findLesson(lessonId)
        .filter(l -> l.getIsActive() != null && l.getIsActive() != 0)
        .filter(l -> l.getPracticePrompt() != null && !l.getPracticePrompt().isBlank())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "lessonId must be an active lesson with a practice prompt"));
  }

  private static String practice(Lesson lesson) {
    return lesson == null ? null : "\"" + lesson.getTitle() + "\": " + lesson.getPracticePrompt();
  }

  private MusicianProfile requireProfile(Long profileId) {
    return nexusRepository
        .findProfile(profileId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown musician"));
  }

  private void requireAllowed(Account account, MusicianProfile profile) {
    boolean self = account.getId().equals(profile.getAccountId());
    boolean owner = "owner".equals(account.getRole());
    if (!self && !owner && !nexusRepository.isInstructor(account.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the musician, a teacher or the owner can use NEXUS for this profile");
    }
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode rubric");
    }
  }
}
