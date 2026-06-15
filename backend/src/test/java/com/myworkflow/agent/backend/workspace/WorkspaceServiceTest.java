package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.audit.InMemoryAuditRepository;
import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceServiceTest {

  @TempDir
  private Path dataRoot;

  @Test
  void resolvesWorkspaceRelativePathsInsideContentRoot() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("user_a", "team_a", "User A")
    );
    WorkspaceService service = serviceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = service.registerWorkspace("Team Knowledge", "main");

    Path resolved = service.resolveContentPath(workspace.workspaceId(), "notes/a.md");

    assertThat(resolved.toString()).startsWith(service.contentRoot(workspace).toString());
    assertThat(resolved.toString()).endsWith(Path.of("notes/a.md").toString());
  }

  @Test
  void rejectsTraversalAndAbsoluteWorkspacePaths() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("user_a", "team_a", "User A")
    );
    WorkspaceService service = serviceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = service.registerWorkspace("Team Knowledge", "main");

    assertThatThrownBy(() -> service.resolveContentPath(workspace.workspaceId(), "../outside.md"))
        .isInstanceOf(InvalidWorkspacePathException.class);
    assertThatThrownBy(() -> service.resolveContentPath(workspace.workspaceId(), "/tmp/outside.md"))
        .isInstanceOf(InvalidWorkspacePathException.class);
  }

  @Test
  void rejectsWorkspaceAccessForDifferentPrincipal() {
    InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("user_a", "team_a", "User A")
    );
    WorkspaceService service = serviceFor(principalProvider, repository);
    WorkspaceRecord workspace = service.registerWorkspace("Team Knowledge", "main");

    principalProvider.setPrincipal(new BackendPrincipal("user_b", "team_b", "User B"));

    assertThatThrownBy(() -> service.getWorkspace(workspace.workspaceId()))
        .isInstanceOf(WorkspaceForbiddenException.class);
  }

  @Test
  void rejectsRepositoryStorageRefsOutsideDataRoot() {
    InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("user_a", "team_a", "User A")
    );
    WorkspaceService service = serviceFor(principalProvider, repository);
    WorkspaceRecord workspace = new WorkspaceRecord(
        "ws_malicious",
        "team_a",
        "Malicious",
        "../outside",
        "main",
        WorkspaceStatus.ACTIVE,
        java.time.Instant.now()
    );
    repository.save(workspace, "user_a", WorkspaceRole.WORKSPACE_OWNER);

    assertThatThrownBy(() -> service.resolveContentPath("ws_malicious", "notes/a.md"))
        .isInstanceOf(InvalidWorkspacePathException.class);
  }

  private WorkspaceService serviceFor(
      PrincipalProvider principalProvider,
      WorkspaceRepository repository
  ) {
    return new WorkspaceService(
        new BackendProperties(
            dataRoot.toString(),
            "user_a",
            "team_a",
            "User A"
        ),
        principalProvider,
        repository,
        new AuditService(principalProvider, new InMemoryAuditRepository())
    );
  }

  private static class MutablePrincipalProvider implements PrincipalProvider {

    private BackendPrincipal principal;

    MutablePrincipalProvider(BackendPrincipal principal) {
      this.principal = principal;
    }

    @Override
    public BackendPrincipal currentPrincipal() {
      return principal;
    }

    void setPrincipal(BackendPrincipal principal) {
      this.principal = principal;
    }
  }
}
