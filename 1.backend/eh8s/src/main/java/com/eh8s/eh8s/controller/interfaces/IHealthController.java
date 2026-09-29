package com.eh8s.eh8s.controller.interfaces;

import java.util.Map;

/**
 * HTTP contract for process health.
 */
public interface IHealthController {

  /**
   * Returns a live health payload.
   *
   * @return status and module identity
   */
  Map<String, String> health();
}
