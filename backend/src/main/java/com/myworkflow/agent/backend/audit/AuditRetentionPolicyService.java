package com.myworkflow.agent.backend.audit;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

@Service
public class AuditRetentionPolicyService {

  private final BackendProperties properties;
  private final WorkspaceService workspaceService;

  public AuditRetentionPolicyService(
      BackendProperties properties,
      WorkspaceService workspaceService
  ) {
    this.properties = properties;
    this.workspaceService = workspaceService;
  }

  public AuditRetentionPolicy retentionPolicyForWorkspace(String workspaceId) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    BackendProperties.AuditRetention retention = properties.auditRetention();
    return new AuditRetentionPolicy(
        workspace.workspaceId(),
        retention.retentionDays(),
        retention.mode(),
        retention.destructivePurgeEnabled(),
        "backend-config"
    );
  }

  public record AuditRetentionPolicy(
      String workspaceId,
      int retentionDays,
      String mode,
      boolean destructivePurgeEnabled,
      String policySource
  ) {
  }
}
