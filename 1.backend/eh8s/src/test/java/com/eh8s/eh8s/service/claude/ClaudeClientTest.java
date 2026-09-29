package com.eh8s.eh8s.service.claude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ClaudeClientTest {

  private HttpServer server;
  private final AtomicReference<String> seenKey = new AtomicReference<>();
  private final AtomicReference<String> seenVersion = new AtomicReference<>();
  private final AtomicReference<String> seenBody = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String reply =
      "{\"id\":\"msg_1\",\"type\":\"message\",\"model\":\"claude-sonnet-4-5\","
          + "\"content\":[{\"type\":\"text\",\"text\":\"- 0 academy payments today.\"}],"
          + "\"usage\":{\"input_tokens\":321,\"output_tokens\":45}}";

  @BeforeEach
  void startStub() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          seenKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          seenVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
          seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] out = reply.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, out.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  private ClaudeClient client(String key) {
    return new ClaudeClient(
        key, "claude-sonnet-4-5", "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @Test
  void sendsMessagesRequestAndParsesTextAndUsage() {
    Map<String, Object> out = client("test-key").complete("system text", "user text", 600);

    assertEquals("test-key", seenKey.get());
    assertEquals(ClaudeClient.ANTHROPIC_VERSION, seenVersion.get());
    assertTrue(seenBody.get().contains("\"model\":\"claude-sonnet-4-5\""));
    assertTrue(seenBody.get().contains("\"max_tokens\":600"));
    assertTrue(seenBody.get().contains("\"system\":\"system text\""));
    assertTrue(seenBody.get().contains("{\"role\":\"user\",\"content\":\"user text\"}"));
    assertEquals("- 0 academy payments today.", out.get("text"));
    assertEquals(321, out.get("inputTokens"));
    assertEquals(45, out.get("outputTokens"));
  }

  @Test
  void missingKeyIs503WithoutCallingTheApi() {
    ClaudeClient c = client("  ");
    assertFalse(c.isConfigured());
    ResponseStatusException e =
        assertThrows(ResponseStatusException.class, () -> c.complete("s", "u", 10));
    assertEquals(503, e.getStatusCode().value());
    assertEquals("AI digest unavailable: ANTHROPIC_API_KEY not set", e.getReason());
    assertEquals(null, seenBody.get());
  }

  @Test
  void apiErrorOrEmptyReplyIs502() {
    status = 401;
    reply = "{\"type\":\"error\"}";
    ResponseStatusException e =
        assertThrows(ResponseStatusException.class, () -> client("bad").complete("s", "u", 10));
    assertEquals(502, e.getStatusCode().value());

    status = 200;
    reply = "{\"content\":[]}";
    e = assertThrows(ResponseStatusException.class, () -> client("k").complete("s", "u", 10));
    assertEquals(502, e.getStatusCode().value());
  }
}
