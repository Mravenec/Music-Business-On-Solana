package com.eh8s.eh8s.service.course;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts lesson video links from the four hosts the academy supports (YouTube, Vimeo, Bunny
 * Stream, Cloudflare Stream) and turns each one into the player URL the browser embeds. Nothing
 * is uploaded or proxied: the video stays on the host.
 */
public final class VideoLinks {

  /** Hard limit of the {@code lesson.video_url} / {@code lesson.embed_url} columns. */
  public static final int MAX_URL_LENGTH = 600;

  private static final String INVALID =
      "videoUrl must be an https link from YouTube, Vimeo, Bunny Stream or Cloudflare Stream";

  private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
  private static final Pattern DIGITS = Pattern.compile("\\d{1,12}");
  private static final Pattern HEX = Pattern.compile("[A-Za-z0-9]{4,40}");
  private static final Pattern BUNNY_PATH =
      Pattern.compile("^/(?:embed|play)/(\\d{1,12})/([0-9a-fA-F-]{36})/?$");
  private static final Pattern CLOUDFLARE_UID = Pattern.compile("[0-9a-f]{32}");
  private static final Pattern CLOUDFLARE_CUSTOMER =
      Pattern.compile("^customer-[a-z0-9]{1,40}\\.cloudflarestream\\.com$");

  private VideoLinks() {}

  /**
   * Validates {@code url} and writes provider, original URL and embed URL onto the lesson.
   *
   * @param lesson lesson POJO to update
   * @param url link pasted by the author
   * @throws ResponseStatusException 400 when the link is not a supported https video link
   */
  public static void apply(Lesson lesson, String url) {
    String trimmed = url == null ? "" : url.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_URL_LENGTH) {
      throw invalid();
    }
    URI uri;
    try {
      uri = new URI(trimmed);
    } catch (URISyntaxException e) {
      throw invalid();
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw invalid();
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    String path = uri.getPath() == null ? "" : uri.getPath();
    String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();

    String provider;
    String embed;
    if (isYoutubeHost(host)) {
      provider = "youtube";
      embed = youtube(host, path, query);
    } else if (host.equals("vimeo.com") || host.equals("www.vimeo.com") || host.equals("player.vimeo.com")) {
      provider = "vimeo";
      embed = vimeo(host, path, query);
    } else if (host.equals("iframe.mediadelivery.net") || host.equals("video.bunnycdn.com")) {
      provider = "bunny";
      embed = bunny(path);
    } else if (host.equals("iframe.videodelivery.net")
        || host.equals("watch.cloudflarestream.com")
        || CLOUDFLARE_CUSTOMER.matcher(host).matches()) {
      provider = "cloudflare";
      embed = cloudflare(host, path);
    } else {
      throw invalid();
    }
    lesson.setVideoProvider(provider);
    lesson.setVideoUrl(trimmed);
    lesson.setEmbedUrl(embed);
  }

  private static boolean isYoutubeHost(String host) {
    return host.equals("youtube.com")
        || host.equals("www.youtube.com")
        || host.equals("m.youtube.com")
        || host.equals("youtu.be")
        || host.equals("www.youtube-nocookie.com");
  }

  private static String youtube(String host, String path, String query) {
    String id;
    if (host.equals("youtu.be")) {
      id = path.startsWith("/") ? path.substring(1) : path;
    } else if (path.equals("/watch")) {
      id = param(query, "v");
    } else if (path.startsWith("/embed/") || path.startsWith("/shorts/") || path.startsWith("/live/")) {
      id = path.substring(path.indexOf('/', 1) + 1);
    } else {
      throw invalid();
    }
    if (id != null && id.endsWith("/")) {
      id = id.substring(0, id.length() - 1);
    }
    if (id == null || !YOUTUBE_ID.matcher(id).matches()) {
      throw invalid();
    }
    String start = seconds(param(query, "t"), param(query, "start"));
    return "https://www.youtube-nocookie.com/embed/" + id + (start == null ? "" : "?start=" + start);
  }

  private static String vimeo(String host, String path, String query) {
    String[] parts = path.replaceAll("^/+|/+$", "").split("/");
    String id;
    String hash = null;
    if (host.equals("player.vimeo.com")) {
      if (parts.length != 2 || !parts[0].equals("video")) {
        throw invalid();
      }
      id = parts[1];
      hash = param(query, "h");
    } else {
      if (parts.length < 1 || parts.length > 2) {
        throw invalid();
      }
      id = parts[0];
      hash = parts.length == 2 ? parts[1] : param(query, "h");
    }
    if (!DIGITS.matcher(id).matches() || (hash != null && !HEX.matcher(hash).matches())) {
      throw invalid();
    }
    return "https://player.vimeo.com/video/" + id + (hash == null ? "" : "?h=" + hash);
  }

  private static String bunny(String path) {
    Matcher m = BUNNY_PATH.matcher(path);
    if (!m.matches()) {
      throw invalid();
    }
    return "https://iframe.mediadelivery.net/embed/" + m.group(1) + "/" + m.group(2).toLowerCase(Locale.ROOT);
  }

  private static String cloudflare(String host, String path) {
    String[] parts = path.replaceAll("^/+|/+$", "").split("/");
    if (parts.length < 1 || parts.length > 2) {
      throw invalid();
    }
    String uid = parts[0].toLowerCase(Locale.ROOT);
    if (!CLOUDFLARE_UID.matcher(uid).matches()) {
      throw invalid();
    }
    if (parts.length == 2 && !parts[1].equals("iframe") && !parts[1].equals("watch")) {
      throw invalid();
    }
    if (CLOUDFLARE_CUSTOMER.matcher(host).matches()) {
      return "https://" + host + "/" + uid + "/iframe";
    }
    return "https://iframe.videodelivery.net/" + uid;
  }

  private static String param(String query, String name) {
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equals(name)) {
        return pair.substring(eq + 1);
      }
    }
    return null;
  }

  private static String seconds(String t, String start) {
    String raw = t != null ? t : start;
    if (raw == null) {
      return null;
    }
    String digits = raw.endsWith("s") ? raw.substring(0, raw.length() - 1) : raw;
    return DIGITS.matcher(digits).matches() ? String.valueOf(Long.parseLong(digits)) : null;
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID);
  }
}
