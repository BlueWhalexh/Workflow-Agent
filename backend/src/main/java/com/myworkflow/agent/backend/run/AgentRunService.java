package com.myworkflow.agent.backend.run;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.approval.ApprovalRepository;
import com.myworkflow.agent.backend.artifact.ArtifactRepository;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialService;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialService.ProviderCredentialRuntimeDescriptor;
import com.myworkflow.agent.backend.providersecret.ProviderRuntimePolicy;
import com.myworkflow.agent.backend.providersecret.ProviderSecretResolver;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AgentRunService {

  private static final String CREDENTIAL_RUNTIME_REF_PREFIX = "credential.";
  private static final String ENV_SECRET_REF_PREFIX = "env://";
  private static final String PROVIDER_CREDENTIAL_API_KEY_ENV_NAME = "PROVIDER_CREDENTIAL_API_KEY";

  private final WorkspaceService workspaceService;
  private final PrincipalProvider principalProvider;
  private final AgentRunRepository repository;
  private final ArtifactRepository artifactRepository;
  private final ApprovalRepository approvalRepository;
  private final RunEventRepository runEventRepository;
  private final AuditService auditService;
  private final ProviderRuntimePolicy providerRuntimePolicy;
  private final ObjectProvider<ProviderCredentialService> providerCredentialServiceProvider;
  private final ObjectProvider<ProviderSecretResolver> providerSecretResolverProvider;
  private final AgentWorker worker;
  private final ExecutorService executorService;

  public AgentRunService(
      WorkspaceService workspaceService,
      PrincipalProvider principalProvider,
      AgentRunRepository repository,
      ArtifactRepository artifactRepository,
      ApprovalRepository approvalRepository,
      RunEventRepository runEventRepository,
      AuditService auditService,
      ProviderRuntimePolicy providerRuntimePolicy,
      ObjectProvider<ProviderCredentialService> providerCredentialServiceProvider,
      ObjectProvider<ProviderSecretResolver> providerSecretResolverProvider,
      AgentWorker worker,
      ExecutorService executorService
  ) {
    this.workspaceService = workspaceService;
    this.principalProvider = principalProvider;
    this.repository = repository;
    this.artifactRepository = artifactRepository;
    this.approvalRepository = approvalRepository;
    this.runEventRepository = runEventRepository;
    this.auditService = auditService;
    this.providerRuntimePolicy = providerRuntimePolicy;
    this.providerCredentialServiceProvider = providerCredentialServiceProvider;
    this.providerSecretResolverProvider = providerSecretResolverProvider;
    this.worker = worker;
    this.executorService = executorService;
  }

  public AgentRunRecord startRun(
      String workspaceId,
      String userMessage,
      String mode,
      boolean execute,
      boolean autoApprove,
      String providerRuntimeRef
  ) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_EDITOR);
    BackendPrincipal principal = principalProvider.currentPrincipal();
    Path workspaceRoot = workspaceService.resolveContentPath(workspace.workspaceId(), "");
    Instant now = Instant.now();
    String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
    String jobId = "job_" + UUID.randomUUID().toString().replace("-", "");
    String normalizedMode = normalizeMode(mode);
    AgentRunRecord run = AgentRunRecord.queued(
        runId,
        workspace.workspaceId(),
        principal.userId(),
        normalizeUserMessage(userMessage),
        normalizedMode,
        execute,
        autoApprove,
        now
    );
    AgentJobRecord job = AgentJobRecord.queued(jobId, runId, now, 3);
    ResolvedProviderRuntime providerRuntime = resolveProviderRuntime(
        workspace.workspaceId(),
        normalizedMode,
        execute,
        providerRuntimeRef
    );
    if (!providerRuntime.secretInjection().isEmpty() && !worker.supportsSecretInjection()) {
      throw new IllegalArgumentException("Provider credential secret refs require a worker that supports secret injection");
    }
    repository.create(run, job);
    auditService.record(run.workspaceId(), run.runId(), "AGENT_RUN_REQUESTED", "Agent run requested");
    appendEvent(run.runId(), "RUN_QUEUED", AgentRunStatus.QUEUED, "Run queued", now);
    executorService.submit(() -> executeRun(run, job, workspaceRoot, providerRuntime));
    return run;
  }

  public AgentRunRecord getRun(String runId) {
    AgentRunRecord run = repository.findRun(runId)
        .orElseThrow(() -> new AgentRunNotFoundException(runId));
    workspaceService.getWorkspace(run.workspaceId());
    return run;
  }

  public AgentRunRecord cancelRun(String runId) {
    AgentRunRecord run = getRun(runId);
    workspaceService.requireWorkspaceRole(run.workspaceId(), WorkspaceRole.WORKSPACE_EDITOR);
    repository.cancel(run.runId(), Instant.now());
    AgentRunRecord canceled = getRun(runId);
    if (run.status() != AgentRunStatus.CANCELED && canceled.status() == AgentRunStatus.CANCELED) {
      auditService.record(canceled.workspaceId(), canceled.runId(), "AGENT_RUN_CANCELED", "Agent run canceled");
      appendEvent(canceled.runId(), "CANCELED", AgentRunStatus.CANCELED, "Run canceled", Instant.now());
    }
    return canceled;
  }

  private void executeRun(
      AgentRunRecord run,
      AgentJobRecord job,
      Path workspaceRoot,
      ResolvedProviderRuntime providerRuntime
  ) {
    String workerKind = worker.workerKind();
    for (int attempt = 1; attempt <= job.maxAttempts(); attempt++) {
      Instant startedAt = Instant.now();
      AgentRunStatus beforeRunning = currentStatus(run.runId());
      repository.markRunning(run.runId(), job.jobId(), workerKind, startedAt);
      if (!appendEventAfterTransition(
          run.runId(),
          beforeRunning,
          AgentRunStatus.QUEUED,
          AgentRunStatus.RUNNING,
          "RUNNING",
          "Worker attempt running",
          startedAt
      )) {
        return;
      }
      try {
        AgentWorkerResponse response = worker.run(new AgentWorkerRequest(
            run.runId(),
            workspaceRoot.toString(),
            run.userMessage(),
            run.mode(),
            run.execute(),
            run.autoApprove(),
            providerRuntime.providerRuntime()
        ), providerRuntime.secretInjection());
        validateResponse(run, response);
        if (isCanceled(run.runId())) {
          return;
        }
        artifactRepository.registerRunArtifacts(
            run.runId(),
            run.workspaceId(),
            response.artifactRefs(),
            Instant.now()
        );
        if (response.requiresApproval()) {
          approvalRepository.createPending(
              run.runId(),
              run.workspaceId(),
              run.requestedByUserId(),
              firstArtifactRef(response),
              response.targetWorkspacePaths(),
              Instant.now()
          );
        }
        AgentRunStatus runStatus = mapRunStatus(response);
        AgentRunStatus beforeComplete = currentStatus(run.runId());
        repository.complete(
            run.runId(),
            job.jobId(),
            workerKind,
            response,
            runStatus,
            Instant.now()
        );
        appendEventAfterTransition(
            run.runId(),
            beforeComplete,
            AgentRunStatus.RUNNING,
            runStatus,
            "COMPLETED",
            "Worker response recorded",
            Instant.now()
        );
        return;
      } catch (RuntimeException exception) {
        if (isCanceled(run.runId())) {
          return;
        }
        if (attempt < job.maxAttempts()) {
          AgentRunStatus beforeRetry = currentStatus(run.runId());
          repository.retry(run.runId(), job.jobId(), "WORKER_FAILED", Instant.now());
          if (!appendEventAfterTransition(
              run.runId(),
              beforeRetry,
              AgentRunStatus.RUNNING,
              AgentRunStatus.QUEUED,
              "RETRY_QUEUED",
              "Worker retry queued",
              Instant.now()
          )) {
            return;
          }
          continue;
        }
        AgentRunStatus beforeFail = currentStatus(run.runId());
        repository.fail(run.runId(), job.jobId(), workerKind, "WORKER_FAILED", Instant.now());
        appendEventAfterTransition(
            run.runId(),
            beforeFail,
            AgentRunStatus.RUNNING,
            AgentRunStatus.FAILED,
            "FAILED",
            "Worker failed",
            Instant.now()
        );
        return;
      }
    }
  }

  private ResolvedProviderRuntime resolveProviderRuntime(
      String workspaceId,
      String mode,
      boolean execute,
      String providerRuntimeRef
  ) {
    String normalizedRef = normalizeOptionalRef(providerRuntimeRef);
    if (normalizedRef != null && normalizedRef.startsWith(CREDENTIAL_RUNTIME_REF_PREFIX)) {
      return resolveCredentialProviderRuntime(workspaceId, normalizedRef);
    }
    return new ResolvedProviderRuntime(
        providerRuntimePolicy.resolve(mode, execute, providerRuntimeRef),
        AgentWorkerSecretInjection.empty()
    );
  }

  private ResolvedProviderRuntime resolveCredentialProviderRuntime(String workspaceId, String providerRuntimeRef) {
    String credentialRef = providerRuntimeRef.substring(CREDENTIAL_RUNTIME_REF_PREFIX.length());
    if (credentialRef.isBlank()) {
      throw new IllegalArgumentException("Provider credential reference is required");
    }
    ProviderCredentialService providerCredentialService = providerCredentialServiceProvider.getIfAvailable();
    if (providerCredentialService == null) {
      throw new IllegalArgumentException("Provider credential references require JDBC provider credential storage");
    }
    ProviderCredentialRuntimeDescriptor descriptor = providerCredentialService
        .resolveRuntimeDescriptorForWorkspace(workspaceId, credentialRef)
        .orElseThrow(() -> new IllegalArgumentException("Unknown provider credential reference"));
    return runtimeFromCredentialDescriptor(descriptor);
  }

  private ResolvedProviderRuntime runtimeFromCredentialDescriptor(
      ProviderCredentialRuntimeDescriptor descriptor
  ) {
    Map<String, Object> runtime = new LinkedHashMap<>();
    runtime.put("provider", descriptor.provider());
    putString(runtime, "model", descriptor.model());
    putString(runtime, "baseUrl", descriptor.baseUrl());
    ResolvedApiKeySecret apiKeySecret = resolveApiKeySecretRef(descriptor.apiKeySecretRef());
    runtime.put("apiKeyEnvName", apiKeySecret.envName());
    runtime.put("timeoutMs", 30_000);
    return new ResolvedProviderRuntime(Map.copyOf(runtime), apiKeySecret.secretInjection());
  }

  private ResolvedApiKeySecret resolveApiKeySecretRef(String secretRef) {
    if (secretRef == null) {
      throw new IllegalArgumentException(
          "Provider credential secret ref is required"
      );
    }
    if (secretRef.startsWith(ENV_SECRET_REF_PREFIX)) {
      String envName = secretRef.substring(ENV_SECRET_REF_PREFIX.length());
      if (envName.isBlank()) {
        throw new IllegalArgumentException("Provider credential env secret ref is required");
      }
      return new ResolvedApiKeySecret(envName, AgentWorkerSecretInjection.empty());
    }
    String secretValue = providerSecretResolverProvider.stream()
        .map(resolver -> resolver.resolveSecretValue(secretRef))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Provider credential secret ref could not be resolved"));
    return new ResolvedApiKeySecret(
        PROVIDER_CREDENTIAL_API_KEY_ENV_NAME,
        new AgentWorkerSecretInjection(Map.of(PROVIDER_CREDENTIAL_API_KEY_ENV_NAME, secretValue))
    );
  }

  private static void putString(Map<String, Object> runtime, String key, String value) {
    if (value != null && !value.isBlank()) {
      runtime.put(key, value.trim());
    }
  }

  private boolean appendEventAfterTransition(
      String runId,
      AgentRunStatus before,
      AgentRunStatus expectedBefore,
      AgentRunStatus expectedAfter,
      String eventType,
      String message,
      Instant now
  ) {
    if (before != expectedBefore || currentStatus(runId) != expectedAfter) {
      return false;
    }
    appendEvent(runId, eventType, expectedAfter, message, now);
    return true;
  }

  private void appendEvent(String runId, String eventType, AgentRunStatus status, String message, Instant now) {
    runEventRepository.append(runId, eventType, status, message, now);
  }

  private AgentRunStatus currentStatus(String runId) {
    return repository.findRun(runId)
        .map(AgentRunRecord::status)
        .orElse(null);
  }

  private boolean isCanceled(String runId) {
    return repository.findRun(runId)
        .map(AgentRunRecord::status)
        .filter((status) -> status == AgentRunStatus.CANCELED)
        .isPresent();
  }

  private static void validateResponse(AgentRunRecord run, AgentWorkerResponse response) {
    if (!"agent-backend-response.v1".equals(response.schemaVersion())) {
      throw new AgentWorkerException("Worker returned unsupported schema version");
    }
    if (!run.runId().equals(response.runId())) {
      throw new AgentWorkerException("Worker returned mismatched run id");
    }
  }

  private static AgentRunStatus mapRunStatus(AgentWorkerResponse response) {
    if (response.requiresConfirmation() || "NEEDS_CONFIRMATION".equals(response.status())) {
      return AgentRunStatus.WAITING_CONFIRMATION;
    }
    if (response.requiresApproval() || "WAITING_APPROVAL".equals(response.status())) {
      return AgentRunStatus.WAITING_APPROVAL;
    }
    return switch (response.status()) {
      case "SUCCEEDED" -> AgentRunStatus.SUCCEEDED;
      case "SUCCEEDED_WITH_WARNINGS" -> AgentRunStatus.SUCCEEDED_WITH_WARNINGS;
      case "FAILED", "FAILED_ROUTE", "FAILED_PROVIDER", "FAILED_POLICY" -> AgentRunStatus.FAILED;
      default -> AgentRunStatus.FAILED;
    };
  }

  private static String firstArtifactRef(AgentWorkerResponse response) {
    if (response.artifactRefs() == null || response.artifactRefs().isEmpty()) {
      return null;
    }
    return response.artifactRefs().get(0);
  }

  private static String normalizeMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return "auto";
    }
    return mode.trim();
  }

  private static String normalizeUserMessage(String userMessage) {
    if (userMessage == null || userMessage.trim().isEmpty()) {
      throw new IllegalArgumentException("userMessage is required");
    }
    return userMessage.trim();
  }

  private static String normalizeOptionalRef(String providerRuntimeRef) {
    if (providerRuntimeRef == null || providerRuntimeRef.isBlank()) {
      return null;
    }
    return providerRuntimeRef.trim();
  }

  private record ResolvedProviderRuntime(
      Map<String, Object> providerRuntime,
      AgentWorkerSecretInjection secretInjection
  ) {
  }

  private record ResolvedApiKeySecret(
      String envName,
      AgentWorkerSecretInjection secretInjection
  ) {
  }
}
