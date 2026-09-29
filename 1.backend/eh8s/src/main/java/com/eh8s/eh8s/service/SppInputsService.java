package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Concert;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.ConcertSetCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.CreativeRating;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalCheckin;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.RehearsalSession;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppCycle;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppVariable;
import com.eh8s.eh8s.repository.interfaces.ISppInputsRepository;
import com.eh8s.eh8s.service.interfaces.ISppInputsService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns rehearsal check-ins, peer ratings, Enigma level deltas, and concert minutes into the five
 * weighted SPP variables when a cycle closes.
 */
@Service
public class SppInputsService implements ISppInputsService {

  static final int DEFAULT_SHOW_MINUTES = 60;
  static final int MAX_RATING = 5;
  static final Map<String, Integer> DEFAULT_WEIGHTS_BPS =
      Map.of("attendance", 4000, "punctuality", 1500, "creative", 2000, "skill", 1500, "concert", 1000);

  private final ISppInputsRepository repository;

  /**
   * Creates the service.
   *
   * @param repository SPP inputs persistence
   */
  public SppInputsService(ISppInputsRepository repository) {
    this.repository = repository;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CreativeRating rate(Long accountId, Long sessionId, CreativeRating rating) {
    Long rater = profileOf(accountId);
    RehearsalSession session =
        repository
            .findSession(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown rehearsal"));
    if (rating == null || rating.getRateeProfileId() == null || rating.getScore() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rateeProfileId and score are required");
    }
    int score = rating.getScore();
    if (score < 1 || score > MAX_RATING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score must be 1..5");
    }
    Long ratee = rating.getRateeProfileId();
    if (rater.equals(ratee)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot rate yourself");
    }
    if (!repository.isMember(session.getBandId(), rater)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this band");
    }
    if (!repository.isMember(session.getBandId(), ratee)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That musician is not in this band");
    }
    if (repository.findCheckin(sessionId, rater).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Check in to this rehearsal before rating");
    }
    if (repository.ratingExists(sessionId, rater, ratee)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You already rated this bandmate");
    }
    CreativeRating row = new CreativeRating();
    row.setRehearsalSessionId(sessionId);
    row.setRaterProfileId(rater);
    row.setRateeProfileId(ratee);
    row.setScore((byte) score);
    return repository.insertRating(row);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CreativeRating> ratings(Long sessionId) {
    return repository.findRatings(sessionId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConcertSetCheckin startSet(Long accountId, Long concertId, ConcertSetCheckin checkin) {
    Long profile = profileOf(accountId);
    Concert concert = concertFor(concertId, profile);
    if (repository.findSetCheckin(concert.getId(), profile).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Set already started");
    }
    ConcertSetCheckin row = new ConcertSetCheckin();
    row.setConcertId(concert.getId());
    row.setMusicianProfileId(profile);
    row.setSetStartedAt(
        checkin != null && checkin.getSetStartedAt() != null ? checkin.getSetStartedAt() : LocalDateTime.now());
    return repository.insertSetCheckin(row);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConcertSetCheckin endSet(Long accountId, Long concertId, ConcertSetCheckin checkin) {
    Long profile = profileOf(accountId);
    Concert concert = concertFor(concertId, profile);
    ConcertSetCheckin row =
        repository
            .findSetCheckin(concert.getId(), profile)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Start your set first"));
    if (row.getSetEndedAt() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Set already ended");
    }
    LocalDateTime end =
        checkin != null && checkin.getSetEndedAt() != null ? checkin.getSetEndedAt() : LocalDateTime.now();
    if (end.isBefore(row.getSetStartedAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set cannot end before it starts");
    }
    long minutes = Duration.between(row.getSetStartedAt(), end).toMinutes();
    row.setSetEndedAt(end);
    row.setMinutesPlayed((int) Math.min(minutes, showMinutes(concert)));
    return repository.updateSetCheckinEnd(row);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<ConcertSetCheckin> setCheckins(Long concertId) {
    return repository.findSetCheckins(concertId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Byte currentEnigmaLevel(Long musicianProfileId) {
    return repository.findEnigmaLevel(musicianProfileId).map(Integer::byteValue).orElse(null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean computeCycleScores(SppCycle cycle, List<SppMemberScore> scores, LocalDate closeDate) {
    LocalDate start = cycle.getStartedAt() == null ? closeDate : cycle.getStartedAt();
    List<RehearsalSession> sessions =
        repository.findSessionsBetween(
            cycle.getBandId(), start.atStartOfDay(), closeDate.plusDays(1).atStartOfDay());
    List<Long> sessionIds = sessions.stream().map(RehearsalSession::getId).toList();
    List<Concert> concerts = repository.findConcertsByCycle(cycle.getId());
    boolean skillCaptured = scores.stream().anyMatch(s -> s.getEnigmaLevelStart() != null);
    if (sessions.isEmpty() && concerts.isEmpty() && !skillCaptured) {
      return false;
    }

    List<RehearsalCheckin> checkins = repository.findCheckinsForSessions(sessionIds);
    List<CreativeRating> ratings = repository.findRatingsForSessions(sessionIds);
    List<Long> concertIds = concerts.stream().map(Concert::getId).toList();
    List<ConcertSetCheckin> sets = repository.findSetCheckinsForConcerts(concertIds);
    Map<Long, Integer> showMinutesByConcert = new HashMap<>();
    int showMinutesTotal = 0;
    for (Concert concert : concerts) {
      int minutes = showMinutes(concert);
      showMinutesByConcert.put(concert.getId(), minutes);
      showMinutesTotal += minutes;
    }
    Map<String, Integer> weights = weights();

    for (SppMemberScore score : scores) {
      Long member = score.getMusicianProfileId();
      long attended = checkins.stream().filter(c -> member.equals(c.getMusicianProfileId())).count();
      long onTime =
          checkins.stream()
              .filter(c -> member.equals(c.getMusicianProfileId()))
              .filter(c -> c.getPunctualityPoints() != null && c.getPunctualityPoints() > 0)
              .count();
      double attendanceRatio = sessions.isEmpty() ? 0 : (double) attended / sessions.size();
      double punctualityRatio = sessions.isEmpty() ? 0 : (double) onTime / sessions.size();

      double creativeRatio =
          ratings.stream()
              .filter(r -> member.equals(r.getRateeProfileId()))
              .mapToInt(r -> r.getScore())
              .average()
              .orElse(0)
              / MAX_RATING;

      Byte endLevel = currentEnigmaLevel(member);
      score.setEnigmaLevelEnd(endLevel);
      double skillRatio = 0;
      if (score.getEnigmaLevelStart() != null && endLevel != null) {
        skillRatio = Math.max(0, Math.min(1, endLevel - score.getEnigmaLevelStart()));
      }

      int played = 0;
      for (ConcertSetCheckin set : sets) {
        if (member.equals(set.getMusicianProfileId()) && set.getMinutesPlayed() != null) {
          int cap = showMinutesByConcert.getOrDefault(set.getConcertId(), DEFAULT_SHOW_MINUTES);
          played += Math.min(set.getMinutesPlayed(), cap);
        }
      }
      double concertRatio = showMinutesTotal == 0 ? 0 : (double) played / showMinutesTotal;

      score.setAttendancePoints(points(attendanceRatio, weights.get("attendance")));
      score.setPunctualityPoints(points(punctualityRatio, weights.get("punctuality")));
      score.setCreativePoints(points(creativeRatio, weights.get("creative")));
      score.setSkillPoints(points(skillRatio, weights.get("skill")));
      score.setConcertPoints(points(concertRatio, weights.get("concert")));
      score.setTotalPoints(
          score.getAttendancePoints()
              + score.getPunctualityPoints()
              + score.getCreativePoints()
              + score.getSkillPoints()
              + score.getConcertPoints());
    }
    return true;
  }

  /**
   * Converts a 0..1 ratio into points; a full ratio earns weight_bps / 10 (1000 across all five).
   *
   * @param ratio achievement ratio 0..1
   * @param weightBps variable weight in basis points
   * @return rounded points
   */
  static int points(double ratio, Integer weightBps) {
    int weight = weightBps == null ? 0 : weightBps;
    return (int) Math.round(Math.max(0, Math.min(1, ratio)) * weight / 10.0);
  }

  private Map<String, Integer> weights() {
    Map<String, Integer> out = new HashMap<>(DEFAULT_WEIGHTS_BPS);
    for (SppVariable variable : repository.findVariables()) {
      if (variable.getCode() != null && variable.getWeightBps() != null) {
        out.put(variable.getCode(), variable.getWeightBps());
      }
    }
    return out;
  }

  private Long profileOf(Long accountId) {
    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
    }
    return repository
        .findMusicianProfileId(accountId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only musicians can do this"));
  }

  private Concert concertFor(Long concertId, Long profile) {
    Concert concert =
        repository
            .findConcert(concertId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown concert"));
    if (!repository.isMember(concert.getBandId(), profile)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this band");
    }
    return concert;
  }

  private static int showMinutes(Concert concert) {
    Integer minutes = concert.getShowMinutes();
    return minutes == null || minutes <= 0 ? DEFAULT_SHOW_MINUTES : minutes;
  }
}
