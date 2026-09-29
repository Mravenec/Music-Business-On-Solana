package com.eh8s.eh8s.service;

import com.eh8s.eh8s.repository.interfaces.IAppMetaRepository;
import com.eh8s.eh8s.service.interfaces.IHealthService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reads module identity from MariaDB for the Health endpoint.
 */
@Service
public class HealthService implements IHealthService {

  private final IAppMetaRepository appMetaRepository;

  /**
   * Creates the service.
   *
   * @param appMetaRepository metadata persistence
   */
  public HealthService(IAppMetaRepository appMetaRepository) {
    this.appMetaRepository = appMetaRepository;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, String> currentHealth() {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("status", "ok");
    body.put(
        "module",
        appMetaRepository.findByKey("module_name").map(r -> r.getMetaValue()).orElse("eh8s"));
    body.put(
        "product",
        appMetaRepository
            .findByKey("product")
            .map(r -> r.getMetaValue())
            .orElse("Enigma H8 Studios"));
    return body;
  }
}
