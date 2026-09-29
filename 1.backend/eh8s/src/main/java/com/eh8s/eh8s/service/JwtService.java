package com.eh8s.eh8s.service;

import com.eh8s.eh8s.service.interfaces.IJwtService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * HS256 JWT issue/parse using {@code JWT_SECRET}.
 */
@Service
public class JwtService implements IJwtService {

  private final SecretKey key;
  private final long ttlSeconds;

  /**
   * Creates the service.
   *
   * @param secret HS256 secret (at least 32 bytes)
   * @param ttlSeconds access token lifetime
   */
  public JwtService(
      @Value("${eh8s.jwt.secret}") String secret,
      @Value("${eh8s.jwt.ttl-seconds:3600}") long ttlSeconds) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("eh8s.jwt.secret / JWT_SECRET must be at least 32 bytes");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
    this.ttlSeconds = ttlSeconds;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String issue(Long accountId, String email) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(accountId))
        .claim("email", email)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(key)
        .compact();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<JwtPrincipal> parse(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      Claims claims =
          Jwts.parser().verifyWith(key).build().parseSignedClaims(token.trim()).getPayload();
      Long id = Long.valueOf(claims.getSubject());
      String email = claims.get("email", String.class);
      return Optional.of(new JwtPrincipal(id, email));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long ttlSeconds() {
    return ttlSeconds;
  }
}
