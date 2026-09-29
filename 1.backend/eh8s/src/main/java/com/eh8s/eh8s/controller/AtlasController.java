package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IAtlasController;
import com.eh8s.eh8s.controller.support.HttpBody;
import com.eh8s.eh8s.service.interfaces.IAtlasService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves ATLAS tour routes.
 */
@RestController
@RequestMapping("/api")
public class AtlasController implements IAtlasController {

  private final IAtlasService atlasService;

  /**
   * Creates the controller.
   *
   * @param atlasService ATLAS use cases
   */
  public AtlasController(IAtlasService atlasService) {
    this.atlasService = atlasService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/bands/{bandId}/tour-routes")
  public Map<String, Object> plans(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long bandId) {
    return atlasService.plans(accountId(principal), bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping("/bands/{bandId}/tour-routes")
  public Map<String, Object> generate(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long bandId,
      @RequestBody(required = false) Map<String, Object> body) {
    return atlasService.generate(
        accountId(principal),
        bandId,
        HttpBody.text(body, "windowStart"),
        HttpBody.text(body, "windowEnd"),
        HttpBody.intValue(body, "maxStops"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/tour-routes/{planId}")
  public Map<String, Object> plan(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long planId) {
    return atlasService.plan(accountId(principal), planId);
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
