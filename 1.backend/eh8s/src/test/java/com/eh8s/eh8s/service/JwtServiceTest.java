package com.eh8s.eh8s.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HS256 issue/parse round trip.
 */
class JwtServiceTest {

  @Test
  void issueAndParseRoundTrip() {
    JwtService jwt = new JwtService("eh8s-dev-jwt-secret-change-me-32b", 3600);
    String token = jwt.issue(42L, "a@eh8s.local");
    var principal = jwt.parse(token);
    assertTrue(principal.isPresent());
    assertEquals(42L, principal.get().accountId());
    assertEquals("a@eh8s.local", principal.get().email());
  }

  @Test
  void parseRejectsGarbage() {
    JwtService jwt = new JwtService("eh8s-dev-jwt-secret-change-me-32b", 3600);
    assertTrue(jwt.parse("not-a-jwt").isEmpty());
  }
}
