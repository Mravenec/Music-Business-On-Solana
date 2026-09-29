package com.eh8s.eh8s.controller.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.List;
import java.util.Map;

/**
 * HTTP contract for Stage Map pins, venue open dates, and slot requests.
 */
public interface IStageMapController {

  /**
   * Lists one pin per venue for a month.
   *
   * @param month optional YYYY-MM (defaults to the current month)
   * @return pin maps
   */
  List<Map<String, Object>> pins(String month);

  /**
   * Loads the pin popup data for one venue.
   *
   * @param venueId venue id
   * @param month optional YYYY-MM (defaults to the current month)
   * @return pin map
   */
  Map<String, Object> pin(Long venueId, String month);

  /**
   * Publishes one open date for a venue.
   *
   * @param principal JWT principal
   * @param venueId venue id
   * @param availability body with availableDate
   * @return stored availability
   */
  VenueAvailability addAvailability(JwtPrincipal principal, Long venueId, VenueAvailability availability);

  /**
   * Requests an open slot for the signed-in musician's band.
   *
   * @param principal JWT principal
   * @param venueId venue id
   * @param booking body with showDate and optional bandId
   * @return booking with status requested
   */
  Booking requestSlot(JwtPrincipal principal, Long venueId, Booking booking);
}
