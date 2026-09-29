package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IAuthController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.service.interfaces.IAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public auth endpoints that issue JWTs.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController implements IAuthController {

  private final IAuthService authService;

  /**
   * Creates the controller.
   *
   * @param authService register/login use cases
   */
  public AuthController(IAuthService authService) {
    this.authService = authService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/register")
  public Map<String, Object> register(@RequestBody(required = false) Map<String, Object> body) {
    return authService.register(
        HttpBody.text(body, "email"),
        HttpBody.raw(body, "password"),
        HttpBody.text(body, "displayName"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody(required = false) Map<String, Object> body) {
    return authService.login(HttpBody.text(body, "email"), HttpBody.raw(body, "password"));
  }
}
