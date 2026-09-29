package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.repository.interfaces.IOpsRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory owner decisions with the same "only while pending" rule as the JOOQ repository.
 */
class OpsRepositoryFake implements IOpsRepository {

  final Map<Long, OwnerDecision> decisions = new LinkedHashMap<>();

  OwnerDecision addPending(long id, String title) {
    OwnerDecision d = new OwnerDecision();
    d.setId(id);
    d.setOpsAgentId(4L);
    d.setTitle(title);
    d.setSemaphore("yellow");
    d.setPayload(title);
    d.setStatus("pending");
    decisions.put(id, d);
    return d;
  }

  @Override
  public List<OpsAgent> findAgents() {
    return new ArrayList<>();
  }

  @Override
  public Optional<OpsAgent> findAgent(Long id) {
    return Optional.empty();
  }

  @Override
  public List<OwnerDecision> findDecisions() {
    return new ArrayList<>(decisions.values());
  }

  @Override
  public Optional<OwnerDecision> findDecision(Long id) {
    return Optional.ofNullable(decisions.get(id));
  }

  @Override
  public Optional<OwnerDecision> approve(Long id, String answeredVia, String answeredBy) {
    return decide(id, "approved", null, answeredVia, answeredBy);
  }

  @Override
  public Optional<OwnerDecision> reject(Long id, String rejectNote, String answeredVia, String answeredBy) {
    return decide(id, "rejected", rejectNote, answeredVia, answeredBy);
  }

  @Override
  public OpsAgent setSemaphore(Long id, String semaphore) {
    throw new UnsupportedOperationException();
  }

  private Optional<OwnerDecision> decide(
      Long id, String status, String reason, String answeredVia, String answeredBy) {
    OwnerDecision d = decisions.get(id);
    if (d == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown decision");
    }
    if (!"pending".equals(d.getStatus())) {
      return Optional.empty();
    }
    d.setStatus(status);
    d.setRejectReason(reason);
    d.setResolvedAt(LocalDateTime.now());
    d.setAnsweredVia(answeredVia);
    d.setAnsweredBy(answeredBy);
    return Optional.of(d);
  }
}
