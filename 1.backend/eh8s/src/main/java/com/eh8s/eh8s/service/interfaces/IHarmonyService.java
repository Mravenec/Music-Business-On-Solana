package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * HARMONY band match: ranks musicians who are not in a band by instrument gap (40), Enigma level
 * proximity (30), country (15) and genre overlap (15). The score is deterministic and explained;
 * Claude only adds an optional one-line rationale per top candidate.
 */
public interface IHarmonyService {

  /**
   * Stored suggestions for a band, best first.
   *
   * @param accountId signed-in account (band member or owner)
   * @param bandId band id
   * @return map with band, aiConfigured and suggestions (suggestion + candidate details)
   * @throws org.springframework.web.server.ResponseStatusException 403 not a member or owner,
   *     404 unknown band
   */
  Map<String, Object> suggestions(Long accountId, Long bandId);

  /**
   * Re-scores every candidate, stores the ranking and logs a HARMONY agent event.
   *
   * @param accountId signed-in account (band member or owner)
   * @param bandId band id
   * @param withAi ask Claude for a rationale on the top candidates when a key is set
   * @return same shape as {@link #suggestions(Long, Long)} plus aiUsed and aiError
   * @throws org.springframework.web.server.ResponseStatusException 403 not a member or owner,
   *     404 unknown band
   */
  Map<String, Object> run(Long accountId, Long bandId, boolean withAi);
}
