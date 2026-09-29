package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.Map;

/**
 * HTTP contract for ATLAS tour routes.
 */
public interface IAtlasController {

  /**
   * Tour plans of a band with stops and the band's active zones.
   *
   * @param principal JWT principal
   * @param bandId band id
   * @return plans payload
   */
  Map<String, Object> plans(JwtPrincipal principal, Long bandId);

  /**
   * Generates a new ATLAS route for a band. Reads windowStart / windowEnd (ISO dates) and optional
   * maxStops (1..10) from the command body and passes them to the service as primitives.
   *
   * @param principal JWT principal
   * @param bandId band id
   * @param body windowStart, windowEnd, optional maxStops
   * @return plan map
   */
  Map<String, Object> generate(JwtPrincipal principal, Long bandId, Map<String, Object> body);

  /**
   * Loads one tour plan.
   *
   * @param principal JWT principal
   * @param planId plan id
   * @return plan map
   */
  Map<String, Object> plan(JwtPrincipal principal, Long planId);
}
