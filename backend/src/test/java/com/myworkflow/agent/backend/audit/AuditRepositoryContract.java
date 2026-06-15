package com.myworkflow.agent.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

final class AuditRepositoryContract {

  private AuditRepositoryContract() {
  }

  static void assertWorkspaceAuditQueryContract(AuditRepository repository) {
    String workspaceId = "ws_audit_contract";
    appendEvent(
        repository,
        workspaceId,
        null,
        "WORKSPACE_CREATED",
        "Workspace created",
        "2030-01-01T00:00:00Z"
    );
    appendEvent(
        repository,
        workspaceId,
        null,
        "WORKSPACE_MEMBER_GRANTED",
        "Workspace member granted",
        "2030-01-01T00:00:01Z"
    );
    appendEvent(
        repository,
        workspaceId,
        "run_contract_1",
        "ARTIFACT_READ",
        "Artifact read",
        "2030-01-01T00:00:02Z"
    );
    appendEvent(
        repository,
        workspaceId,
        "run_contract_2",
        "APPROVAL_DECIDED",
        "Approval decided",
        "2030-01-01T00:00:03Z"
    );
    appendEvent(
        repository,
        "ws_other_audit_contract",
        "run_contract_3",
        "ARTIFACT_READ",
        "Other workspace artifact read",
        "2030-01-01T00:00:04Z"
    );

    assertThat(repository.findByWorkspaceId(workspaceId, new AuditEventQuery(2, 1, null, null)))
        .extracting(AuditEventRecord::eventType)
        .containsExactly("WORKSPACE_MEMBER_GRANTED", "ARTIFACT_READ");
    assertThat(repository.findByWorkspaceId(workspaceId, new AuditEventQuery(10, 0, "ARTIFACT_READ", null)))
        .extracting(AuditEventRecord::runId)
        .containsExactly("run_contract_1");
    assertThat(repository.findByWorkspaceId(workspaceId, new AuditEventQuery(10, 0, null, "run_contract_2")))
        .extracting(AuditEventRecord::eventType)
        .containsExactly("APPROVAL_DECIDED");
  }

  private static void appendEvent(
      AuditRepository repository,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      String createdAt
  ) {
    repository.append(
        "owner_audit_contract",
        "team_audit_contract",
        workspaceId,
        runId,
        eventType,
        message,
        Instant.parse(createdAt)
    );
  }
}
