package com.myworkflow.agent.backend.approval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApprovalRepository {

  ApprovalRequestRecord createPending(
      String runId,
      String workspaceId,
      String requestedByUserId,
      String artifactRef,
      List<String> targetWorkspacePaths,
      Instant now
  );

  List<ApprovalRequestRecord> findByRunId(String runId);

  Optional<ApprovalRequestRecord> findById(String approvalId);

  ApprovalRequestRecord decide(
      String approvalId,
      String decidedByUserId,
      ApprovalDecision decision,
      Instant now
  );
}
