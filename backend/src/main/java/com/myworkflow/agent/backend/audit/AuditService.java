package com.myworkflow.agent.backend.audit;

import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final PrincipalProvider principalProvider;
  private final AuditRepository auditRepository;

  public AuditService(
      PrincipalProvider principalProvider,
      AuditRepository auditRepository
  ) {
    this.principalProvider = principalProvider;
    this.auditRepository = auditRepository;
  }

  public AuditEventRecord record(
      String workspaceId,
      String runId,
      String eventType,
      String message
  ) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    return auditRepository.append(
        principal.userId(),
        principal.teamId(),
        workspaceId,
        runId,
        eventType,
        message,
        Instant.now()
    );
  }
}
