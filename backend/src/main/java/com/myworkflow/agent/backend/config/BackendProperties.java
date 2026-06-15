package com.myworkflow.agent.backend.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackendProperties {

  private final Path dataRoot;
  private final DevPrincipal devPrincipal;

  public BackendProperties(
      @Value("${my-workflow.backend.data-root:${java.io.tmpdir}/my-workflow-agent-backend}") String dataRoot,
      @Value("${my-workflow.backend.dev-principal.user-id:dev-user}") String devUserId,
      @Value("${my-workflow.backend.dev-principal.team-id:dev-team}") String devTeamId,
      @Value("${my-workflow.backend.dev-principal.display-name:Dev User}") String devDisplayName
  ) {
    this.dataRoot = Path.of(dataRoot).toAbsolutePath().normalize();
    this.devPrincipal = new DevPrincipal(devUserId, devTeamId, devDisplayName);
  }

  public Path dataRoot() {
    return dataRoot;
  }

  public DevPrincipal devPrincipal() {
    return devPrincipal;
  }

  public record DevPrincipal(
      String userId,
      String teamId,
      String displayName
  ) {
  }
}
