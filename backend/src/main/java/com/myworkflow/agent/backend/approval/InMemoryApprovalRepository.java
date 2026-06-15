package com.myworkflow.agent.backend.approval;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryApprovalRepository implements ApprovalRepository {

  private final Map<String, ApprovalRequestRecord> approvals = new LinkedHashMap<>();

  @Override
  public synchronized ApprovalRequestRecord createPending(
      String runId,
      String workspaceId,
      String requestedByUserId,
      String artifactRef,
      List<String> targetWorkspacePaths,
      Instant now
  ) {
    ApprovalRequestRecord approval = ApprovalRequestFactory.createPending(
        runId,
        workspaceId,
        requestedByUserId,
        artifactRef,
        targetWorkspacePaths,
        now
    );
    approvals.put(approval.approvalId(), approval);
    return approval;
  }

  @Override
  public synchronized List<ApprovalRequestRecord> findByRunId(String runId) {
    return approvals.values().stream()
        .filter((approval) -> approval.runId().equals(runId))
        .toList();
  }

  @Override
  public synchronized Optional<ApprovalRequestRecord> findById(String approvalId) {
    return Optional.ofNullable(approvals.get(approvalId));
  }

  @Override
  public synchronized ApprovalRequestRecord decide(
      String approvalId,
      String decidedByUserId,
      ApprovalDecision decision,
      Instant now
  ) {
    ApprovalRequestRecord existing = findById(approvalId)
        .orElseThrow(() -> new ApprovalNotFoundException(approvalId));
    ApprovalRequestRecord decided = new ApprovalRequestRecord(
        existing.approvalId(),
        existing.runId(),
        existing.workspaceId(),
        existing.requestedByUserId(),
        decidedByUserId,
        decision,
        ApprovalStatus.DECIDED,
        existing.artifactRef(),
        existing.targetWorkspacePaths(),
        existing.createdAt(),
        now
    );
    approvals.put(approvalId, decided);
    return decided;
  }
}
