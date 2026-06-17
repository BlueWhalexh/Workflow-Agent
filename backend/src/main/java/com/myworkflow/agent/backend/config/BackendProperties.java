package com.myworkflow.agent.backend.config;

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackendProperties {

  private final Path dataRoot;
  private final Path providerSecretFileRoot;
  private final DevPrincipal devPrincipal;
  private final AuditRetention auditRetention;

  @Autowired
  public BackendProperties(
      @Value("${my-workflow.backend.data-root:${java.io.tmpdir}/my-workflow-agent-backend}") String dataRoot,
      @Value("${my-workflow.backend.dev-principal.user-id:dev-user}") String devUserId,
      @Value("${my-workflow.backend.dev-principal.team-id:dev-team}") String devTeamId,
      @Value("${my-workflow.backend.dev-principal.display-name:Dev User}") String devDisplayName,
      @Value("${my-workflow.backend.provider-secrets.file-root:}") String providerSecretFileRoot,
      @Value("${my-workflow.backend.audit.retention-days:365}") int auditRetentionDays
  ) {
    this.dataRoot = Path.of(dataRoot).toAbsolutePath().normalize();
    this.providerSecretFileRoot = blankToNull(providerSecretFileRoot)
        .map(value -> Path.of(value).toAbsolutePath().normalize())
        .orElse(null);
    this.devPrincipal = new DevPrincipal(devUserId, devTeamId, devDisplayName);
    this.auditRetention = new AuditRetention(auditRetentionDays, "REPORT_ONLY", false);
  }

  public BackendProperties(
      String dataRoot,
      String devUserId,
      String devTeamId,
      String devDisplayName
  ) {
    this(dataRoot, devUserId, devTeamId, devDisplayName, "", 365);
  }

  public Path dataRoot() {
    return dataRoot;
  }

  public Optional<Path> providerSecretFileRoot() {
    return Optional.ofNullable(providerSecretFileRoot);
  }

  public DevPrincipal devPrincipal() {
    return devPrincipal;
  }

  public AuditRetention auditRetention() {
    return auditRetention;
  }

  public record DevPrincipal(
      String userId,
      String teamId,
      String displayName
  ) {
  }

  public record AuditRetention(
      int retentionDays,
      String mode,
      boolean destructivePurgeEnabled
  ) {
    public AuditRetention {
      if (retentionDays < 1 || retentionDays > 3650) {
        throw new IllegalArgumentException("Audit retention days must be between 1 and 3650");
      }
    }
  }

  private static Optional<String> blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }
}
