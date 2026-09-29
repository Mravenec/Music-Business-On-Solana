package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IHarmonyController;
import com.eh8s.eh8s.service.interfaces.IHarmonyService;
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
 * Serves HARMONY band match suggestions.
 */
@RestController
@RequestMapping("/api/bands/{bandId}/match-suggestions")
public class HarmonyController implements IHarmonyController {

  private final IHarmonyService harmonyService;

  /**
   * Creates the controller.
   *
   * @param harmonyService HARMONY use cases
   */
  public HarmonyController(IHarmonyService harmonyService) {
    this.harmonyService = harmonyService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping
  public Map<String, Object> suggestions(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long bandId) {
    return harmonyService.suggestions(accountId(principal), bandId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @PostMapping
  public Map<String, Object> run(
      @AuthenticationPrincipal JwtPrincipal principal,
      @PathVariable Long bandId,
      @RequestBody(required = false) Map<String, Object> body) {
    boolean withAi = body != null && Boolean.parseBoolean(String.valueOf(body.get("withAi")));
    return harmonyService.run(accountId(principal), bandId, withAi);
  }

  private static Long accountId(JwtPrincipal principal) {
    return principal == null ? null : principal.accountId();
  }
}
