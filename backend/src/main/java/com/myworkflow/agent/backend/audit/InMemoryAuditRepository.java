package com.myworkflow.agent.backend.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryAuditRepository implements AuditRepository {

  private final List<AuditEventRecord> events = new ArrayList<>();

  @Override
  public synchronized AuditEventRecord append(
      String actorUserId,
      String teamId,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      Instant createdAt
  ) {
    AuditEventRecord event = new AuditEventRecord(
        "aud_" + UUID.randomUUID().toString().replace("-", ""),
        actorUserId,
        teamId,
        workspaceId,
        runId,
        eventType,
        message,
        createdAt
    );
    events.add(event);
    return event;
  }

  @Override
  public synchronized List<AuditEventRecord> findByRunId(String runId) {
    return events.stream()
        .filter((event) -> runId.equals(event.runId()))
        .toList();
  }

  @Override
  public synchronized List<AuditEventRecord> findByWorkspaceId(String workspaceId, AuditEventQuery query) {
    return events.stream()
        .filter((event) -> workspaceId.equals(event.workspaceId()))
        .filter((event) -> query.eventType() == null || query.eventType().equals(event.eventType()))
        .filter((event) -> query.runId() == null || query.runId().equals(event.runId()))
        .sorted(Comparator.comparing(AuditEventRecord::createdAt)
            .thenComparing(AuditEventRecord::auditEventId))
        .skip(query.offset())
        .limit(query.limit())
        .toList();
  }
}
