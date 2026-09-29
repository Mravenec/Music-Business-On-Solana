package com.eh8s.eh8s.repository;

import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OpsAgent;
import com.eh8s.eh8s.database.jooq.eh8s_ops.tables.pojos.OwnerDecision;
import com.eh8s.eh8s.repository.interfaces.IOpsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.OPS_AGENT;
import static com.eh8s.eh8s.database.jooq.eh8s_ops.Tables.OWNER_DECISION;

/**
 * JOOQ persistence for the owner dashboard.
 */
@Repository
public class OpsRepository implements IOpsRepository {

  private static final Set<String> SEMAPHORES = Set.of("green", "yellow", "red");

  private final DSLContext dsl;

  /**
   * Creates the repository.
   *
   * @param dsl JOOQ context
   */
  public OpsRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<OpsAgent> findAgents() {
    return dsl.selectFrom(OPS_AGENT).orderBy(OPS_AGENT.ID).fetchInto(OpsAgent.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<OpsAgent> findAgent(Long id) {
    return dsl.selectFrom(OPS_AGENT).where(OPS_AGENT.ID.eq(id)).fetchOptionalInto(OpsAgent.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<OwnerDecision> findDecisions() {
    return dsl.selectFrom(OWNER_DECISION).orderBy(OWNER_DECISION.ID).fetchInto(OwnerDecision.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<OwnerDecision> findDecision(Long id) {
    return dsl.selectFrom(OWNER_DECISION).where(OWNER_DECISION.ID.eq(id)).fetchOptionalInto(OwnerDecision.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<OwnerDecision> approve(Long id, String answeredVia, String answeredBy) {
    return decideOnce(id, "approved", null, "executable", answeredVia, answeredBy);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<OwnerDecision> reject(
      Long id, String rejectNote, String answeredVia, String answeredBy) {
    String note = rejectNote == null || rejectNote.isBlank() ? "Rejected by owner" : rejectNote.trim();
    if (note.length() > 500) {
      note = note.substring(0, 500);
    }
    return decideOnce(id, "rejected", note, "blocked", answeredVia, answeredBy);
  }

  private Optional<OwnerDecision> decideOnce(
      Long id,
      String status,
      String rejectReason,
      String taskStatus,
      String answeredVia,
      String answeredBy) {
    OwnerDecision decision =
        findDecision(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown decision"));
    String by = answeredBy == null || answeredBy.isBlank() ? null : answeredBy.trim();
    if (by != null && by.length() > 80) {
      by = by.substring(0, 80);
    }
    int updated =
        dsl.update(OWNER_DECISION)
            .set(OWNER_DECISION.STATUS, status)
            .set(OWNER_DECISION.REJECT_REASON, rejectReason)
            .set(OWNER_DECISION.RESOLVED_AT, LocalDateTime.now())
            .set(OWNER_DECISION.ANSWERED_VIA, answeredVia)
            .set(OWNER_DECISION.ANSWERED_BY, by)
            .where(OWNER_DECISION.ID.eq(id))
            .and(OWNER_DECISION.STATUS.eq("pending"))
            .execute();
    if (updated == 0) {
      return Optional.empty();
    }
    dsl.update(OPS_AGENT)
        .set(OPS_AGENT.TASK_STATUS, taskStatus)
        .where(OPS_AGENT.ID.eq(decision.getOpsAgentId()))
        .execute();
    return findDecision(id);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OpsAgent setSemaphore(Long id, String semaphore) {
    if (semaphore == null || !SEMAPHORES.contains(semaphore.toLowerCase())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semaphore must be green, yellow, or red");
    }
    String value = semaphore.toLowerCase();
    int updated =
        dsl.update(OPS_AGENT)
            .set(OPS_AGENT.SEMAPHORE, value)
            .where(OPS_AGENT.ID.eq(id))
            .execute();
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown agent");
    }
    return findAgent(id).orElseThrow();
  }
}
