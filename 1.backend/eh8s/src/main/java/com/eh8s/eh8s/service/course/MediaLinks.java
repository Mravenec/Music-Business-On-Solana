package com.eh8s.eh8s.service.course;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Official lesson media helpers: validates the owner-pinned integrity fields and signs short-lived
 * watch URLs with HMAC-SHA256. A signature binds the storage object, the key id, the viewer and
 * the expiry, so a leaked link stops working after {@code exp} and cannot be reused by another
 * account. The media host verifies the same HMAC with the shared secret; no permanent public link
 * to an official object is ever issued.
 */
public final class MediaLinks {

  /** Hard limit of the {@code lesson.storage_ref} column. */
  public static final int MAX_STORAGE_REF = 600;

  /** Hard limit of the {@code lesson.key_id} column. */
  public static final int MAX_KEY_ID = 64;

  /** Shortest accepted link lifetime in seconds. */
  public static final long MIN_TTL_SECONDS = 30;

  /** Longest accepted link lifetime in seconds. */
  public static final long MAX_TTL_SECONDS = 900;

  private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
  private static final Pattern STORAGE_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._~/-]*$");
  private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9._-]+$");
  private static final String HMAC = "HmacSHA256";

  private MediaLinks() {}

  /**
   * Tells whether any official media field is set. A half-pinned lesson counts as official so it
   * never falls back to a public embed.
   *
   * @param lesson lesson POJO
   * @return {@code true} when content hash, storage ref or key id is present
   */
  public static boolean isOfficial(Lesson lesson) {
    return present(lesson.getContentHash()) || present(lesson.getStorageRef()) || present(lesson.getKeyId());
  }

  /**
   * Tells whether all three official media fields are pinned.
   *
   * @param lesson lesson POJO
   * @return {@code true} when a signed URL can be issued
   */
  public static boolean isFullyPinned(Lesson lesson) {
    return present(lesson.getContentHash()) && present(lesson.getStorageRef()) && present(lesson.getKeyId());
  }

  /**
   * Validates a content hash.
   *
   * @param raw lowercase or uppercase SHA-256 hex
   * @return normalized lowercase hex
   * @throws IllegalArgumentException when it is not 64 hex characters
   */
  public static String contentHash(String raw) {
    String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if (!SHA256_HEX.matcher(value).matches()) {
      throw new IllegalArgumentException("contentHash must be a SHA-256 hex string (64 characters)");
    }
    return value;
  }

  /**
   * Validates a storage reference: a relative object key, never a URL and never a path escape.
   *
   * @param raw object key
   * @return trimmed key
   * @throws IllegalArgumentException when it is empty, too long or unsafe
   */
  public static String storageRef(String raw) {
    String value = raw.trim();
    if (value.length() > MAX_STORAGE_REF
        || !STORAGE_REF.matcher(value).matches()
        || value.contains("..")
        || value.contains("//")) {
      throw new IllegalArgumentException(
          "storageRef must be a relative object key (letters, digits, . _ ~ - /), at most "
              + MAX_STORAGE_REF
              + " characters");
    }
    return value;
  }

  /**
   * Validates a key identifier.
   *
   * @param raw key id
   * @return trimmed key id
   * @throws IllegalArgumentException when it is too long or has unsupported characters
   */
  public static String keyId(String raw) {
    String value = raw.trim();
    if (value.length() > MAX_KEY_ID || !KEY_ID.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "keyId must use letters, digits, . _ - and be at most " + MAX_KEY_ID + " characters");
    }
    return value;
  }

  /**
   * Clamps a configured lifetime into the allowed range.
   *
   * @param seconds configured value
   * @return value between {@link #MIN_TTL_SECONDS} and {@link #MAX_TTL_SECONDS}
   */
  public static long clampTtl(long seconds) {
    return Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, seconds));
  }

  /**
   * Builds the signed URL for one viewer.
   *
   * @param baseUrl media host base, without trailing slash
   * @param secret shared HMAC secret
   * @param storageRef pinned object key
   * @param keyId pinned key id
   * @param accountId viewer account id
   * @param expiresAtEpochSec expiry as epoch seconds
   * @return {@code baseUrl/storageRef?exp=&kid=&sub=&sig=}
   */
  public static String signedUrl(
      String baseUrl, String secret, String storageRef, String keyId, long accountId, long expiresAtEpochSec) {
    String sig = signature(secret, storageRef, keyId, accountId, expiresAtEpochSec);
    return trimEnd(baseUrl)
        + "/"
        + storageRef
        + "?exp="
        + expiresAtEpochSec
        + "&kid="
        + URLEncoder.encode(keyId, StandardCharsets.UTF_8)
        + "&sub="
        + accountId
        + "&sig="
        + sig;
  }

  /**
   * Verifies a signature the way the media host does.
   *
   * @param secret shared HMAC secret
   * @param storageRef object key from the path
   * @param keyId {@code kid} query value
   * @param accountId {@code sub} query value
   * @param expiresAtEpochSec {@code exp} query value
   * @param signature {@code sig} query value
   * @param nowEpochSec current time as epoch seconds
   * @return {@code true} when the signature matches and has not expired
   */
  public static boolean verify(
      String secret,
      String storageRef,
      String keyId,
      long accountId,
      long expiresAtEpochSec,
      String signature,
      long nowEpochSec) {
    if (signature == null || nowEpochSec >= expiresAtEpochSec) {
      return false;
    }
    String expected = signature(secret, storageRef, keyId, accountId, expiresAtEpochSec);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
  }

  private static String signature(String secret, String storageRef, String keyId, long accountId, long exp) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
      String payload = storageRef + "\n" + keyId + "\n" + accountId + "\n" + exp;
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is not available", e);
    }
  }

  private static String trimEnd(String base) {
    String value = base == null ? "" : base.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
