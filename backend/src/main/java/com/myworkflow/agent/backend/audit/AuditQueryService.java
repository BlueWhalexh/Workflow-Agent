package com.myworkflow.agent.backend.audit;

import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

  private final AuditRepository auditRepository;
  private final WorkspaceService workspaceService;

  public AuditQueryService(
      AuditRepository auditRepository,
      WorkspaceService workspaceService
  ) {
    this.auditRepository = auditRepository;
    this.workspaceService = workspaceService;
  }

  public List<AuditEventRecord> listWorkspaceAuditEvents(String workspaceId) {
    return listWorkspaceAuditEvents(workspaceId, AuditEventQuery.defaultQuery());
  }

  public List<AuditEventRecord> listWorkspaceAuditEvents(String workspaceId, AuditEventQuery query) {
    workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    return auditRepository.findByWorkspaceId(workspaceId, query);
  }
}
