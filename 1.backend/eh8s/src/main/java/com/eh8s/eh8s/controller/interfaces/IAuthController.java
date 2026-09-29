package com.eh8s.eh8s.controller.interfaces;

import java.util.Map;

/**
 * Public JWT login and register HTTP API.
 */
public interface IAuthController {

  /**
   * Registers a new account and returns a Bearer token.
   *
   * @param body credentials JSON ({@code email}, {@code password}, optional {@code displayName});
   *     read here and passed to the service as primitives
   * @return token JSON
   */
  Map<String, Object> register(Map<String, Object> body);

  /**
   * Logs in with email/password and returns a Bearer token.
   *
   * @param body credentials JSON ({@code email}, {@code password}); read here and passed to the
   *     service as primitives
   * @return token JSON
   */
  Map<String, Object> login(Map<String, Object> body);
}
