package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.Map;

/**
 * HTTP contract for HARMONY band match suggestions.
 */
public interface IHarmonyController {

  /**
   * Stored suggestions for a band.
   *
   * @param principal JWT principal
   * @param bandId band id
   * @return suggestions payload
   */
  Map<String, Object> suggestions(JwtPrincipal principal, Long bandId);

  /**
   * Re-ranks candidates for a band.
   *
   * @param principal JWT principal
   * @param bandId band id
   * @param body optional withAi flag
   * @return suggestions payload plus aiUsed / aiError
   */
  Map<String, Object> run(JwtPrincipal principal, Long bandId, Map<String, Object> body);
}
