package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * Email/password register and login that issue JWTs.
 */
public interface IAuthService {

  /**
   * Creates an account with a BCrypt password hash and returns a JWT.
   *
   * @param email login email
   * @param password plaintext password (hashed with BCrypt, never stored or returned)
   * @param displayName optional display name (defaults to the email)
   * @return non-table token JSON: {@code accessToken}, {@code tokenType}, {@code expiresIn},
   *     {@code accountId}, {@code email}
   */
  Map<String, Object> register(String email, String password, String displayName);

  /**
   * Verifies email/password and returns a JWT.
   *
   * @param email login email
   * @param password plaintext password
   * @return non-table token JSON (same shape as {@link #register(String, String, String)})
   */
  Map<String, Object> login(String email, String password);
}
