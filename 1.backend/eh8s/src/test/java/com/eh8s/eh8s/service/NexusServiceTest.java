package com.eh8s.eh8s.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Account;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.EnigmaLevel;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianLevelChange;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.MusicianProfile;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import com.eh8s.eh8s.repository.interfaces.INexusRepository;
import com.eh8s.eh8s.service.claude.ClaudeClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NexusServiceTest {

  private final List<EnigmaEvaluation> stored = new ArrayList<>();
  private final Map<Long, Byte> scores = new HashMap<>();
  private final List<String> prompts = new ArrayList<>();
  private final AgentEventsFake events = new AgentEventsFake();
  private String reply =
      "Here is the result:\n```json\n{\"score\": 78.4, \"strengths\": [\"Steady groove\", \"Clean tone\"],"
          + " \"errors\": [\"Rushes fills\"], \"exercises\": [\"Metronome 80 bpm, 10 min\"],"
          + " \"recommendedLevel\": 5, \"summary\": \"Ready for Stage level work.\"}\n```";

  private final INexusRepository repo =
      new INexusRepository() {
        @Override
        public Optional<Account> findAccount(Long accountId) {
          Account a = new Account();
          a.setId(accountId);
          a.setRole(accountId == 1L ? "owner" : "musician");
          return accountId > 20 ? Optional.empty() : Optional.of(a);
        }

        @Override
        public Optional<MusicianProfile> findProfile(Long id) {
          if (id != 4L) {
            return Optional.empty();
          }
          MusicianProfile p = new MusicianProfile();
          p.setId(4L);
          p.setAccountId(5L);
          p.setInstrumentId(7L);
          p.setEnigmaLevelId(103L);
          p.setEnigmaScore((byte) 70);
          return Optional.of(p);
        }

        @Override
        public Optional<Long> findProfileIdByAccount(Long accountId) {
          return accountId == 5L ? Optional.of(4L) : Optional.empty();
        }

        @Override
        public boolean isInstructor(Long accountId) {
          return accountId == 11L;
        }

        @Override
        public Optional<String> findInstrumentCode(Long instrumentId) {
          return Optional.of("bass");
        }

        @Override
        public List<EnigmaLevel> findLevels() {
          List<EnigmaLevel> out = new ArrayList<>();
          String[] names = {"Orientation Enigma", "Foundations", "Builder", "Ensemble", "Stage", "Professional EH8S"};
          for (int i = 0; i < names.length; i++) {
            EnigmaLevel l = new EnigmaLevel();
            l.setId(100L + i);
            l.setLevelNumber((byte) i);
            l.setName(names[i]);
            l.setMilestone("milestone " + i);
            out.add(l);
          }
          return out;
        }

        @Override
        public EnigmaEvaluation insertEvaluation(EnigmaEvaluation e) {
          e.setId((long) stored.size() + 1);
          stored.add(e);
          return e;
        }

        @Override
        public void updateEnigmaScore(Long musicianProfileId, byte score) {
          scores.put(musicianProfileId, score);
        }

        @Override
        public List<EnigmaEvaluation> findEvaluations(Long musicianProfileId) {
          return stored;
        }

        @Override
        public Optional<EnigmaEvaluation> findEvaluation(Long id) {
          return stored.stream().filter(e -> e.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<MusicianLevelChange> findLevelChangeSince(
            Long musicianProfileId, byte newLevel, LocalDateTime since) {
          if (newLevel != 4) {
            return Optional.empty();
          }
          MusicianLevelChange c = new MusicianLevelChange();
          c.setNewLevel((byte) 4);
          c.setCreatedAt(since.plusMinutes(5));
          return Optional.of(c);
        }

        @Override
        public void markApplied(Long evaluationId, LocalDateTime appliedAt) {
          findEvaluation(evaluationId).ifPresent(e -> e.setAppliedAt(appliedAt));
        }

        @Override
        public Optional<Lesson> findLesson(Long lessonId) {
          if (lessonId != 30L && lessonId != 31L) {
            return Optional.empty();
          }
          Lesson l = new Lesson();
          l.setId(lessonId);
          l.setTitle("Ghost notes");
          l.setIsActive((byte) 1);
          l.setPracticePrompt(lessonId == 30L ? "Record 16 bars of ghost notes at 80 bpm." : null);
          return Optional.of(l);
        }
      };

  private ClaudeClient claude(String key) {
    return new ClaudeClient(key, "claude-sonnet-4-5", "http://127.0.0.1:9") {
      @Override
      public Map<String, Object> complete(String system, String user, int maxTokens) {
        prompts.add(system + "\n---\n" + user);
        Map<String, Object> out = new HashMap<>();
        out.put("text", reply);
        out.put("model", "claude-sonnet-4-5");
        return out;
      }
    };
  }

  private static EnigmaEvaluation body(int technique) {
    EnigmaEvaluation input = new EnigmaEvaluation();
    input.setMusicianProfileId(4L);
    input.setRubricJson(
        "{\"technique\": " + technique + ", \"timing\": 8, \"tone\": 7, \"expression\": \"7\", \"theory\": 6}");
    input.setRecordingUrl("https://example.com/take-3.mp3");
    input.setNotes("Good pocket, fills rush at bar 16.");
    return input;
  }

  @Test
  void scoresWithClaudeStoresEvaluationAndRaisesYellowEventForLevelChange() {
    NexusService service = new NexusService(repo, claude("k"), events);

    Map<String, Object> out = service.evaluate(11L, body(8));

    EnigmaEvaluation e = stored.get(0);
    assertEquals((byte) 78, e.getScore());
    assertEquals((byte) 4, e.getRecommendedLevel(), "one step above level 3, not 5");
    assertEquals("nexus_ai", e.getSource());
    assertEquals(11L, e.getEvaluatorAccountId());
    assertEquals("Steady groove\nClean tone", e.getStrengths());
    assertEquals("Rushes fills", e.getErrorsFound());
    assertTrue(e.getRubricJson().contains("\"technique\":8"));
    assertEquals((byte) 78, scores.get(4L));
    assertEquals(Boolean.TRUE, out.get("levelChange"));
    assertEquals("NEXUS", events.codes.get(0));
    assertEquals("yellow", events.recorded.get(0).getSemaphore());
    assertTrue(prompts.get(0).contains("You cannot hear audio"));
    assertTrue(prompts.get(0).contains("Recording link (not listened to): https://example.com/take-3.mp3"));
  }

  @Test
  void withoutKeyAnswers503AndStoresNothing() {
    NexusService service = new NexusService(repo, claude(""), events);
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.evaluate(5L, body(8)));
    assertEquals(503, ex.getStatusCode().value());
    assertTrue(stored.isEmpty());
    assertTrue(events.recorded.isEmpty());
  }

  @Test
  void rejectsBadRubricStrangersAndUnknownMusicians() {
    NexusService service = new NexusService(repo, claude("k"), events);
    assertEquals(
        400,
        assertThrows(ResponseStatusException.class, () -> service.evaluate(5L, body(11)))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.evaluate(9L, body(8)))
            .getStatusCode()
            .value());
    EnigmaEvaluation unknown = body(8);
    unknown.setMusicianProfileId(99L);
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.evaluate(1L, unknown))
            .getStatusCode()
            .value());
    EnigmaEvaluation broken = body(8);
    broken.setRubricJson("{not json");
    assertEquals(
        400,
        assertThrows(ResponseStatusException.class, () -> service.evaluate(5L, broken))
            .getStatusCode()
            .value());
  }

  @Test
  void practiceFromALessonIsLinkedAndSentToClaude() {
    NexusService service = new NexusService(repo, claude("k"), events);
    EnigmaEvaluation practice = body(8);
    practice.setLessonId(30L);
    service.evaluate(5L, practice);
    assertEquals(30L, stored.get(0).getLessonId());
    assertTrue(prompts.get(0).contains("Practice exercise being answered: \"Ghost notes\": Record 16 bars"));

    EnigmaEvaluation noPrompt = body(8);
    noPrompt.setLessonId(31L);
    EnigmaEvaluation unknown = body(8);
    unknown.setLessonId(99L);
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.evaluate(5L, noPrompt)).getStatusCode().value());
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.evaluate(5L, unknown)).getStatusCode().value());
    assertEquals(1, stored.size());
  }

  @Test
  void replyWithoutJsonScoreIs502AndHistoryMarksAppliedLevel() {
    NexusService service = new NexusService(repo, claude("k"), events);
    reply = "I cannot score this.";
    assertEquals(
        502,
        assertThrows(ResponseStatusException.class, () -> service.evaluate(5L, body(8)))
            .getStatusCode()
            .value());

    reply = "{\"score\": 81, \"recommendedLevel\": 4, \"summary\": \"ok\"}";
    service.evaluate(5L, body(8));
    List<Map<String, Object>> history = service.evaluations(5L, null);
    EnigmaEvaluation e = (EnigmaEvaluation) history.get(0).get("evaluation");
    assertNotNull(e.getAppliedAt(), "verified level change to 4 marks the evaluation applied");
  }
}
