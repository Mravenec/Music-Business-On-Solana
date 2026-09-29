package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.EnigmaEvaluation;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for NEXUS Score Enigma evaluations.
 */
public interface INexusController {

  /**
   * AI availability, rubric keys and the Enigma level ladder.
   *
   * @return status map
   */
  Map<String, Object> status();

  /**
   * Requests a Score Enigma from Claude for one musician.
   *
   * @param principal JWT principal
   * @param input evaluation POJO: {@code musicianProfileId}, {@code rubricJson} (JSON object of
   *     the five rubric scores), optional {@code recordingUrl}, {@code notes} and {@code lessonId}
   *     (academy lesson whose practice prompt the recording answers)
   * @return evaluation map
   */
  Map<String, Object> evaluate(JwtPrincipal principal, EnigmaEvaluation input);

  /**
   * Lists evaluations for one musician (defaults to the caller).
   *
   * @param principal JWT principal
   * @param musicianProfileId optional musician profile id
   * @return evaluation maps newest first
   */
  List<Map<String, Object>> evaluations(JwtPrincipal principal, Long musicianProfileId);

  /**
   * Loads one evaluation.
   *
   * @param principal JWT principal
   * @param evaluationId evaluation id
   * @return evaluation map
   */
  Map<String, Object> evaluation(JwtPrincipal principal, Long evaluationId);
}
