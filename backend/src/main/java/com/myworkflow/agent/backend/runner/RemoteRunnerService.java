package com.myworkflow.agent.backend.runner;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("jdbc")
public class RemoteRunnerService {

  private static final String RUNNER_RECORD_ID_PREFIX = "rr_";
  private static final Pattern SAFE_RUNNER_REF_PATTERN =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern SAFE_CAPABILITY_PATTERN =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
  private static final Pattern SAFE_LEASE_OWNER_PATTERN =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

  private final WorkspaceService workspaceService;
  private final RemoteRunnerRepository repository;
  private final AuditService auditService;

  public RemoteRunnerService(
      WorkspaceService workspaceService,
      RemoteRunnerRepository repository,
      AuditService auditService
  ) {
    this.workspaceService = workspaceService;
    this.repository = repository;
    this.auditService = auditService;
  }

  public List<RemoteRunnerPublicMetadata> listWorkspaceRunners(String workspaceId) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    return repository.listByWorkspace(workspace.workspaceId()).stream()
        .map(RemoteRunnerService::toPublicMetadata)
        .toList();
  }

  public RemoteRunnerPublicMetadata upsertWorkspaceRunner(
      String workspaceId,
      String runnerRef,
      String displayName,
      String endpointUrl,
      List<String> capabilities
  ) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedRunnerRef = normalizeRunnerRef(runnerRef);
    Instant now = Instant.now();
    RemoteRunnerRecord saved = repository.save(new RemoteRunnerRecord(
        runnerRecordId(workspace.workspaceId(), normalizedRunnerRef),
        workspace.workspaceId(),
        normalizedRunnerRef,
        requireDisplayName(displayName),
        requireEndpointUrl(endpointUrl),
        RemoteRunnerStatus.REGISTERED,
        normalizeCapabilities(capabilities),
        null,
        null,
        null,
        now,
        now
    ));
    auditService.record(
        workspace.workspaceId(),
        null,
        "REMOTE_RUNNER_REGISTERED",
        "Remote runner registered: runnerRef=%s".formatted(saved.runnerRef())
    );
    return toPublicMetadata(saved);
  }

  public RemoteRunnerPublicMetadata recordHeartbeat(String workspaceId, String runnerRef) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedRunnerRef = normalizeRunnerRef(runnerRef);
    RemoteRunnerRecord heartbeat = repository.recordHeartbeat(
            workspace.workspaceId(),
            normalizedRunnerRef,
            Instant.now()
        )
        .orElseThrow(() -> new IllegalArgumentException("Remote runner not found"));
    auditService.record(
        workspace.workspaceId(),
        null,
        "REMOTE_RUNNER_HEARTBEAT",
        "Remote runner heartbeat: runnerRef=%s".formatted(heartbeat.runnerRef())
    );
    return toPublicMetadata(heartbeat);
  }

  public RemoteRunnerPublicMetadata claimLease(
      String workspaceId,
      String runnerRef,
      String leaseOwner,
      int leaseTtlSeconds
  ) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedRunnerRef = normalizeRunnerRef(runnerRef);
    String normalizedLeaseOwner = requireLeaseOwner(leaseOwner);
    int normalizedLeaseTtlSeconds = requireLeaseTtlSeconds(leaseTtlSeconds);
    Instant now = Instant.now();
    RemoteRunnerRecord leased = repository.claimLease(
            workspace.workspaceId(),
            normalizedRunnerRef,
            normalizedLeaseOwner,
            now,
            now.plusSeconds(normalizedLeaseTtlSeconds)
        )
        .orElseThrow(() -> new IllegalArgumentException("Remote runner lease is already held or runner is missing"));
    auditService.record(
        workspace.workspaceId(),
        null,
        "REMOTE_RUNNER_LEASED",
        "Remote runner leased: runnerRef=%s leaseOwner=%s"
            .formatted(leased.runnerRef(), leased.leaseOwner())
    );
    return toPublicMetadata(leased);
  }

  private static RemoteRunnerPublicMetadata toPublicMetadata(RemoteRunnerRecord runner) {
    return new RemoteRunnerPublicMetadata(
        runner.workspaceId(),
        runner.runnerRef(),
        runner.displayName(),
        runner.endpointUrl(),
        runner.status().name(),
        runner.capabilities(),
        runner.lastSeenAt(),
        runner.leaseOwner(),
        runner.leaseExpiresAt()
    );
  }

  private static String normalizeRunnerRef(String runnerRef) {
    if (runnerRef == null || runnerRef.isBlank()) {
      throw new IllegalArgumentException("Remote runner reference is required");
    }
    String normalized = runnerRef.trim();
    if (!SAFE_RUNNER_REF_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid remote runner reference");
    }
    return normalized;
  }

  private static String requireDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("Remote runner display name is required");
    }
    return displayName.trim();
  }

  private static String requireEndpointUrl(String endpointUrl) {
    if (endpointUrl == null || endpointUrl.isBlank()) {
      throw new IllegalArgumentException("Remote runner endpoint URL is required");
    }
    try {
      URI uri = new URI(endpointUrl.trim());
      if (!List.of("http", "https").contains(uri.getScheme())) {
        throw new IllegalArgumentException("Remote runner endpoint URL must use http or https");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("Remote runner endpoint URL host is required");
      }
      if (uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Remote runner endpoint URL must not include credentials");
      }
      return uri.toString();
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("Remote runner endpoint URL is invalid", exception);
    }
  }

  private static List<String> normalizeCapabilities(List<String> capabilities) {
    if (capabilities == null || capabilities.isEmpty()) {
      throw new IllegalArgumentException("Remote runner capabilities are required");
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String capability : capabilities) {
      if (capability == null || capability.isBlank()) {
        throw new IllegalArgumentException("Remote runner capability is required");
      }
      String value = capability.trim();
      if (!SAFE_CAPABILITY_PATTERN.matcher(value).matches()) {
        throw new IllegalArgumentException("Remote runner capability is invalid");
      }
      normalized.add(value);
    }
    if (normalized.size() > 32) {
      throw new IllegalArgumentException("Remote runner capability count is too large");
    }
    return List.copyOf(normalized);
  }

  private static String requireLeaseOwner(String leaseOwner) {
    if (leaseOwner == null || leaseOwner.isBlank()) {
      throw new IllegalArgumentException("Remote runner lease owner is required");
    }
    String normalized = leaseOwner.trim();
    if (!SAFE_LEASE_OWNER_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Remote runner lease owner is invalid");
    }
    return normalized;
  }

  private static int requireLeaseTtlSeconds(int leaseTtlSeconds) {
    if (leaseTtlSeconds < 1 || leaseTtlSeconds > 3600) {
      throw new IllegalArgumentException("Remote runner lease TTL must be between 1 and 3600 seconds");
    }
    return leaseTtlSeconds;
  }

  private static String runnerRecordId(String workspaceId, String runnerRef) {
    String source = workspaceId + "\n" + runnerRef;
    return RUNNER_RECORD_ID_PREFIX + UUID.nameUUIDFromBytes(
        source.getBytes(StandardCharsets.UTF_8)
    ).toString().replace("-", "");
  }

  public record RemoteRunnerPublicMetadata(
      String workspaceId,
      String runnerRef,
      String displayName,
      String endpointUrl,
      String status,
      List<String> capabilities,
      Instant lastSeenAt,
      String leaseOwner,
      Instant leaseExpiresAt
  ) {
  }
}
