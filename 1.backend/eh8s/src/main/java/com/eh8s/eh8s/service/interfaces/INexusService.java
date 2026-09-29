package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import java.util.List;
import java.util.Map;

/**
 * NEXUS Score Enigma: a teacher or the musician submits a recording link, rubric scores and
 * notes; Claude returns a 0..100 score, strengths, errors, exercises and a level recommendation.
 * The level itself only changes on-chain through the NEXUS level page.
 */
public interface INexusService {

  /**
   * AI availability, rubric keys and the Enigma level ladder for the request form.
   *
   * @return map with aiConfigured, model, rubricKeys, levels
   */
  Map<String, Object> status();

  /**
   * Scores one evaluation with Claude, stores it, updates the musician's Score Enigma and logs a
   * NEXUS agent event (yellow + owner decision when a level change is recommended).
   *
   * @param accountId signed-in account
   * @param input evaluation POJO carrying {@code musicianProfileId}, {@code rubricJson} (a JSON
   *     object {technique, timing, tone, expression, theory} each 0..10), optional
   *     {@code recordingUrl} (http/https), {@code notes} and {@code lessonId} (an active academy
   *     lesson with a practice prompt; stored on the evaluation, 400 otherwise); every other
   *     column is ignored and filled by NEXUS
   * @return map with evaluation, currentLevel, recommendedLevel, levelChange
   * @throws org.springframework.web.server.ResponseStatusException 400 bad input, 403 when the
   *     caller is neither the musician, a teacher nor the owner, 404 unknown musician, 503 without
   *     ANTHROPIC_API_KEY, 502 when Claude fails or replies without a valid score
   */
  Map<String, Object> evaluate(Long accountId, EnigmaEvaluation input);

  /**
   * Evaluations for one musician, newest first.
   *
   * @param accountId signed-in account
   * @param musicianProfileId musician (defaults to the caller's own profile)
   * @return evaluation maps (evaluation + applied flag)
   * @throws org.springframework.web.server.ResponseStatusException 400 no musician, 403 not
   *     allowed, 404 unknown musician
   */
  List<Map<String, Object>> evaluations(Long accountId, Long musicianProfileId);

  /**
   * One evaluation with the musician's current level, so the UI can offer the level page.
   *
   * @param accountId signed-in account
   * @param evaluationId evaluation id
   * @return map with evaluation, musician, currentLevel, levelChange
   * @throws org.springframework.web.server.ResponseStatusException 403 not allowed, 404 unknown
   */
  Map<String, Object> evaluation(Long accountId, Long evaluationId);
}
