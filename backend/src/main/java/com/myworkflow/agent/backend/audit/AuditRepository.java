package com.myworkflow.agent.backend.audit;

import java.time.Instant;
import java.util.List;

public interface AuditRepository {

  AuditEventRecord append(
      String actorUserId,
      String teamId,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      Instant createdAt
  );

  List<AuditEventRecord> findByRunId(String runId);

  default List<AuditEventRecord> findByWorkspaceId(String workspaceId) {
    return findByWorkspaceId(workspaceId, AuditEventQuery.defaultQuery());
  }

  List<AuditEventRecord> findByWorkspaceId(String workspaceId, AuditEventQuery query);
}
