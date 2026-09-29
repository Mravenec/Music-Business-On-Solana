package com.eh8s.eh8s.controller.support;

import com.eh8s.eh8s.database.jooq.eh8s_catalog.tables.pojos.RoyaltySplit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads primitive values from a non-table JSON command body at the HTTP edge, so controllers pass
 * primitives (never the raw map) to services. Table-shaped bodies bind to JOOQ POJOs instead.
 */
public final class HttpBody {

  private HttpBody() {}

  /**
   * Returns a trimmed string value, or {@code null} when absent or blank.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return trimmed text or {@code null}
   */
  public static String text(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    if (raw == null) {
      return null;
    }
    String value = raw.toString().trim();
    return value.isEmpty() ? null : value;
  }

  /**
   * Returns the raw string value without trimming, or {@code null} when absent.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return untrimmed text or {@code null}
   */
  public static String raw(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    return raw == null ? null : raw.toString();
  }

  /**
   * Returns a whole number value, or {@code null} when absent or blank.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return long value or {@code null}
   * @throws ResponseStatusException 400 when the value is not a whole number
   */
  public static Long longValue(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    if (raw == null || raw.toString().isBlank()) {
      return null;
    }
    if (raw instanceof Number n && !(raw instanceof Double) && !(raw instanceof Float)) {
      return n.longValue();
    }
    try {
      return new BigDecimal(raw.toString().trim()).longValueExact();
    } catch (NumberFormatException | ArithmeticException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a whole number");
    }
  }

  /**
   * Returns an integer value, or {@code null} when absent or blank.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return int value or {@code null}
   * @throws ResponseStatusException 400 when the value is not a whole number in int range
   */
  public static Integer intValue(Map<String, Object> body, String key) {
    Long value = longValue(body, key);
    if (value == null) {
      return null;
    }
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is out of range");
    }
    return value.intValue();
  }

  /**
   * Returns a decimal value, or {@code null} when absent or blank.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return decimal or {@code null}
   * @throws ResponseStatusException 400 when the value is not a number
   */
  public static BigDecimal decimal(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    if (raw == null || raw.toString().isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(raw.toString().trim());
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a number");
    }
  }

  /**
   * Returns a boolean value, or {@code null} when absent.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return boolean or {@code null}
   * @throws ResponseStatusException 400 when the value is not true/false
   */
  public static Boolean bool(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    if (raw == null) {
      return null;
    }
    if (raw instanceof Boolean b) {
      return b;
    }
    String text = raw.toString().trim().toLowerCase();
    if ("true".equals(text)) {
      return Boolean.TRUE;
    }
    if ("false".equals(text)) {
      return Boolean.FALSE;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be true or false");
  }

  /**
   * Returns a list of trimmed strings, or {@code null} when absent.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return list of strings or {@code null}
   * @throws ResponseStatusException 400 when the value is not a JSON array
   */
  public static List<String> textList(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    if (raw == null) {
      return null;
    }
    if (!(raw instanceof List<?> items)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be an array");
    }
    List<String> out = new ArrayList<>();
    for (Object item : items) {
      out.add(item == null ? null : item.toString().trim());
    }
    return out;
  }

  /**
   * Reads {@code [{musicianProfileId, bps}]} into {@link RoyaltySplit} POJOs (bps lands in
   * {@code shareBps}), or {@code null} when the key is absent or not an array.
   *
   * @param body JSON body (may be {@code null})
   * @param key field name
   * @return split POJOs or {@code null}
   * @throws ResponseStatusException 400 when an item is not {musicianProfileId, bps} numbers
   */
  public static List<RoyaltySplit> royaltySplits(Map<String, Object> body, String key) {
    Object raw = body == null ? null : body.get(key);
    if (!(raw instanceof List<?> items)) {
      return null;
    }
    List<RoyaltySplit> out = new ArrayList<>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> row)
          || !(row.get("musicianProfileId") instanceof Number pid)
          || !(row.get("bps") instanceof Number bps)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, key + " must be [{musicianProfileId, bps > 0}]");
      }
      RoyaltySplit split = new RoyaltySplit();
      split.setMusicianProfileId(pid.longValue());
      split.setShareBps(bps.intValue());
      out.add(split);
    }
    return out;
  }
}
