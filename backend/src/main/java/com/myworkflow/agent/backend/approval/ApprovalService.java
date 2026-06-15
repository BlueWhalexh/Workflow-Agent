package com.myworkflow.agent.backend.approval;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import com.myworkflow.agent.backend.run.AgentRunRecord;
import com.myworkflow.agent.backend.run.AgentRunService;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {

  private final AgentRunService agentRunService;
  private final WorkspaceService workspaceService;
  private final PrincipalProvider principalProvider;
  private final ApprovalRepository approvalRepository;
  private final AuditService auditService;

  public ApprovalService(
      AgentRunService agentRunService,
      WorkspaceService workspaceService,
      PrincipalProvider principalProvider,
      ApprovalRepository approvalRepository,
      AuditService auditService
  ) {
    this.agentRunService = agentRunService;
    this.workspaceService = workspaceService;
    this.principalProvider = principalProvider;
    this.approvalRepository = approvalRepository;
    this.auditService = auditService;
  }

  public List<ApprovalRequestRecord> listRunApprovals(String runId) {
    agentRunService.getRun(runId);
    return approvalRepository.findByRunId(runId);
  }

  public ApprovalRequestRecord decide(String runId, String approvalId, ApprovalDecision decision) {
    AgentRunRecord run = agentRunService.getRun(runId);
    workspaceService.requireWorkspaceRole(run.workspaceId(), WorkspaceRole.WORKSPACE_EDITOR);
    ApprovalRequestRecord approval = approvalRepository.findById(resolveApprovalId(runId, approvalId))
        .orElseThrow(() -> new ApprovalNotFoundException(resolveApprovalId(runId, approvalId)));
    if (!approval.runId().equals(run.runId())) {
      throw new ApprovalNotFoundException(approval.approvalId());
    }
    BackendPrincipal principal = principalProvider.currentPrincipal();
    ApprovalRequestRecord decided = approvalRepository.decide(
        approval.approvalId(),
        principal.userId(),
        decision,
        Instant.now()
    );
    auditService.record(run.workspaceId(), run.runId(), "APPROVAL_DECIDED", "Approval decided");
    return decided;
  }

  private String resolveApprovalId(String runId, String approvalId) {
    if (approvalId != null && !approvalId.isBlank()) {
      return approvalId;
    }
    return approvalRepository.findByRunId(runId).stream()
        .filter((approval) -> approval.status() == ApprovalStatus.PENDING)
        .findFirst()
        .map(ApprovalRequestRecord::approvalId)
        .orElseThrow(() -> new ApprovalNotFoundException("pending approval for run " + runId));
  }
}
