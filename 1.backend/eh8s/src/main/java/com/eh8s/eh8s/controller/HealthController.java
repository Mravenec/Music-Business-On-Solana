package com.eh8s.eh8s.controller;

import com.eh8s.eh8s.controller.interfaces.IHealthController;
import com.eh8s.eh8s.service.interfaces.IHealthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves {@code GET /health} for monitoring and the Vite shell.
 */
@RestController
public class HealthController implements IHealthController {

  private final IHealthService healthService;

  /**
   * Creates the controller.
   *
   * @param healthService health use cases
   */
  public HealthController(IHealthService healthService) {
    this.healthService = healthService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @GetMapping("/health")
  public Map<String, String> health() {
    return healthService.currentHealth();
  }
}
