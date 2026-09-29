package com.eh8s.eh8s.service.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eh8s.eh8s.database.jooq.eh8s_academy.tables.pojos.Lesson;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VideoLinksTest {

  private static Lesson apply(String url) {
    Lesson lesson = new Lesson();
    VideoLinks.apply(lesson, url);
    return lesson;
  }

  @Test
  void youtubeFormsBecomePrivacyEmbed() {
    String embed = "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ";
    assertEquals(embed, apply("https://www.youtube.com/watch?v=dQw4w9WgXcQ").getEmbedUrl());
    assertEquals(embed, apply("https://youtu.be/dQw4w9WgXcQ").getEmbedUrl());
    assertEquals(embed, apply("https://www.youtube.com/embed/dQw4w9WgXcQ").getEmbedUrl());
    assertEquals(embed, apply("https://www.youtube.com/shorts/dQw4w9WgXcQ").getEmbedUrl());
    assertEquals(embed, apply("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ").getEmbedUrl());
    assertEquals(embed + "?start=42", apply("https://youtu.be/dQw4w9WgXcQ?t=42s").getEmbedUrl());
    Lesson lesson = apply("  https://youtu.be/dQw4w9WgXcQ  ");
    assertEquals("youtube", lesson.getVideoProvider());
    assertEquals("https://youtu.be/dQw4w9WgXcQ", lesson.getVideoUrl());
  }

  @Test
  void vimeoKeepsPrivateHash() {
    assertEquals("https://player.vimeo.com/video/76979871", apply("https://vimeo.com/76979871").getEmbedUrl());
    assertEquals(
        "https://player.vimeo.com/video/76979871?h=8272103f6e",
        apply("https://vimeo.com/76979871/8272103f6e").getEmbedUrl());
    assertEquals(
        "https://player.vimeo.com/video/76979871?h=8272103f6e",
        apply("https://player.vimeo.com/video/76979871?h=8272103f6e").getEmbedUrl());
    assertEquals("vimeo", apply("https://vimeo.com/76979871").getVideoProvider());
  }

  @Test
  void bunnyPlayAndEmbedLinks() {
    String guid = "3f0b9d2c-1a2b-4c3d-9e8f-0123456789ab";
    String embed = "https://iframe.mediadelivery.net/embed/12345/" + guid;
    assertEquals(embed, apply("https://iframe.mediadelivery.net/play/12345/" + guid).getEmbedUrl());
    assertEquals(embed, apply("https://video.bunnycdn.com/play/12345/" + guid).getEmbedUrl());
    assertEquals("bunny", apply(embed).getVideoProvider());
  }

  @Test
  void cloudflareStreamLinks() {
    String uid = "5d5bc37ffcf54c9b82e996823bffbb81";
    assertEquals("https://iframe.videodelivery.net/" + uid, apply("https://iframe.videodelivery.net/" + uid).getEmbedUrl());
    assertEquals("https://iframe.videodelivery.net/" + uid, apply("https://watch.cloudflarestream.com/" + uid).getEmbedUrl());
    assertEquals(
        "https://customer-abc123.cloudflarestream.com/" + uid + "/iframe",
        apply("https://customer-abc123.cloudflarestream.com/" + uid + "/watch").getEmbedUrl());
    assertEquals("cloudflare", apply("https://iframe.videodelivery.net/" + uid).getVideoProvider());
  }

  @Test
  void rejectsUnsupportedOrUnsafeLinks() {
    for (String bad :
        new String[] {
          null,
          "",
          "http://youtu.be/dQw4w9WgXcQ",
          "https://youtube.com.evil.io/watch?v=dQw4w9WgXcQ",
          "https://evil.io/?u=https://youtu.be/dQw4w9WgXcQ",
          "https://www.youtube.com/watch?v=short",
          "https://www.youtube.com/channel/UC123",
          "javascript:alert(1)",
          "https://vimeo.com/channels/staffpicks",
          "https://iframe.mediadelivery.net/embed/12345/not-a-guid",
          "https://iframe.videodelivery.net/not-hex",
          "https://customer-x.cloudflarestream.com.evil.io/5d5bc37ffcf54c9b82e996823bffbb81",
          "https://drive.google.com/file/d/abc/view",
          "https://youtu.be/" + "a".repeat(700)
        }) {
      assertThrows(ResponseStatusException.class, () -> apply(bad), String.valueOf(bad));
    }
  }
}
