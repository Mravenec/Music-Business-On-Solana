package com.eh8s.eh8s.service.interfaces;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.Booking;
import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.VenueAvailability;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Stage Map pins derived from real data: purple partner, yellow upcoming confirmed
 * booking, green approved venue with open dates, red no availability that month. Also venue open
 * dates and the musician "Request slot" action.
 */
public interface IStageMapService {

  /**
   * Builds one pin per venue for a month.
   *
   * @param month calendar month (null = current month)
   * @return pin maps: venueId, venue, pin, pinLabel, contractType, availableDays, nextShow, month
   */
  List<Map<String, Object>> pins(YearMonth month);

  /**
   * Builds the pin popup data for one venue.
   *
   * @param venueId venue id
   * @param month calendar month (null = current month)
   * @return pin map (same keys as {@link #pins(YearMonth)})
   */
  Map<String, Object> pin(Long venueId, YearMonth month);

  /**
   * Publishes one open date for a venue. When the venue has a wallet, only that wallet's account
   * may publish dates.
   *
   * @param accountId account id from the JWT
   * @param venueId venue id
   * @param availability body with availableDate (today or later)
   * @return stored availability row
   */
  VenueAvailability addAvailability(Long accountId, Long venueId, VenueAvailability availability);

  /**
   * Creates a booking request (status requested) for the signed-in musician's band on an open date.
   *
   * @param accountId account id from the JWT
   * @param venueId venue id
   * @param booking body with showDate and optional bandId (required when the musician has several bands)
   * @return stored booking with status requested
   */
  Booking requestSlot(Long accountId, Long venueId, Booking booking);
}
