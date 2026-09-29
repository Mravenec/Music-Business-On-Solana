package com.eh8s.eh8s.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Web inbox answers: recorded as answeredVia = web, and a second answer is 409.
 */
class OpsServiceTest {

  @Test
  void webApproveRecordsSourceAndRefusesSecondAnswer() {
    OpsRepositoryFake repo = new OpsRepositoryFake();
    repo.addPending(1L, "Booking review");
    OpsService service = new OpsService(repo);

    assertEquals("web", service.approve(1L).getAnsweredVia());
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.reject(1L, "late"));
    assertEquals(409, ex.getStatusCode().value());
    assertEquals("approved", repo.decisions.get(1L).getStatus());
  }

  @Test
  void unknownDecisionIs404() {
    OpsService service = new OpsService(new OpsRepositoryFake());
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.approve(99L)).getStatusCode().value());
  }
}
