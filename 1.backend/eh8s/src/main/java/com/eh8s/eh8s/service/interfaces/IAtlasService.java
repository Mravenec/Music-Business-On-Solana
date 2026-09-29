package com.eh8s.eh8s.service.interfaces;

import java.util.List;
import java.util.Map;

/**
 * ATLAS tour routes: for a band and a date window, a nearest-neighbour route across approved
 * venues inside the band's active geographic subscriptions, starting at the venue with
 * the highest projected income. Income = capacity x suggested ticket x 60% fill x contract band
 * share.
 */
public interface IAtlasService {

  /**
   * Generates and stores a route (tour_plan + tour_plan_stop) and logs an ATLAS agent event.
   *
   * @param accountId signed-in account (band member or owner)
   * @param bandId band id
   * @param windowStart first show date (YYYY-MM-DD, not in the past)
   * @param windowEnd last show date (YYYY-MM-DD, window at most 92 days)
   * @param maxStops optional 1..10 ({@code null} = 6)
   * @return plan map (plan + stops with venue details)
   * @throws org.springframework.web.server.ResponseStatusException 400 bad window, 403 not a member
   *     or owner, 404 unknown band, 409 no active geographic subscription or no approved venue in
   *     the band's zones
   */
  Map<String, Object> generate(
      Long accountId, Long bandId, String windowStart, String windowEnd, Integer maxStops);

  /**
   * Tour plans of a band, newest first, with their stops.
   *
   * @param accountId signed-in account (band member or owner)
   * @param bandId band id
   * @return map with band, activeZones and plans
   * @throws org.springframework.web.server.ResponseStatusException 403 not allowed, 404 unknown
   */
  Map<String, Object> plans(Long accountId, Long bandId);

  /**
   * One tour plan with its stops.
   *
   * @param accountId signed-in account (band member or owner)
   * @param planId plan id
   * @return plan map
   * @throws org.springframework.web.server.ResponseStatusException 403 not allowed, 404 unknown
   */
  Map<String, Object> plan(Long accountId, Long planId);

  /**
   * Active zone codes of a band on a day (used by the plans page header).
   *
   * @param bandId band id
   * @return zone codes
   */
  List<String> activeZones(Long bandId);
}
