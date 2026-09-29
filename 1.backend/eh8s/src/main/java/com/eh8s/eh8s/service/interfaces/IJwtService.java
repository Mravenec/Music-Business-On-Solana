package com.eh8s.eh8s.service.interfaces;

/**
 * Issues and parses self-issued HS256 JWTs. Does not touch the database.
 */
public interface IJwtService {

  /**
   * Creates a signed access token for an account.
   *
   * @param accountId account primary key
   * @param email account email (claim)
   * @return compact JWT
   */
  String issue(Long accountId, String email);

  /**
   * Parses a compact JWT and returns the subject account id when valid.
   *
   * @param token compact JWT without the Bearer prefix
   * @return principal, or empty if the token is missing or invalid
   */
  java.util.Optional<JwtPrincipal> parse(String token);

  /**
   * Access-token lifetime in seconds (for JSON responses).
   *
   * @return ttl seconds
   */
  long ttlSeconds();
}
