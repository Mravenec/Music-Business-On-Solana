package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.AppMeta;
import com.eh8s.eh8s.repository.interfaces.IAppMetaRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link HealthService}.
 */
class HealthServiceTest {

  @Test
  void currentHealthReadsModuleFromRepository() {
    IAppMetaRepository repo =
        key -> {
          if ("module_name".equals(key)) {
            return Optional.of(new AppMeta(key, "eh8s", LocalDateTime.now()));
          }
          if ("product".equals(key)) {
            return Optional.of(new AppMeta(key, "Enigma H8 Studios", LocalDateTime.now()));
          }
          return Optional.empty();
        };
    HealthService service = new HealthService(repo);
    Map<String, String> body = service.currentHealth();
    assertEquals("ok", body.get("status"));
    assertEquals("eh8s", body.get("module"));
    assertEquals("Enigma H8 Studios", body.get("product"));
  }
}
