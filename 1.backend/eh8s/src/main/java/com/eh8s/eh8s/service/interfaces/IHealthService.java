package com.eh8s.eh8s.service.interfaces;

import java.util.Map;

/**
 * Health checks for the EH8S API.
 */
public interface IHealthService {

  /**
   * Builds a live health payload from {@code app_meta}.
   *
   * @return status plus module identity fields
   */
  Map<String, String> currentHealth();
}
