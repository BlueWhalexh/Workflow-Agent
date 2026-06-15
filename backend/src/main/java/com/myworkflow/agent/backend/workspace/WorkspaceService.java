package com.myworkflow.agent.backend.workspace;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

  private final BackendProperties properties;
  private final PrincipalProvider principalProvider;
  private final WorkspaceRepository repository;
  private final AuditService auditService;

  public WorkspaceService(
      BackendProperties properties,
      PrincipalProvider principalProvider,
      WorkspaceRepository repository,
      AuditService auditService
  ) {
    this.properties = properties;
    this.principalProvider = principalProvider;
    this.repository = repository;
    this.auditService = auditService;
  }

  public WorkspaceRecord registerWorkspace(String name, String defaultBranch) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedName = normalizeName(name);
    String workspaceId = "ws_" + UUID.randomUUID().toString().replace("-", "");
    String branch = normalizeDefaultBranch(defaultBranch);
    String serverStorageRef = "teams/%s/workspaces/%s/content".formatted(principal.teamId(), workspaceId);

    WorkspaceRecord workspace = new WorkspaceRecord(
        workspaceId,
        principal.teamId(),
        normalizedName,
        serverStorageRef,
        branch,
        WorkspaceStatus.ACTIVE,
        Instant.now()
    );

    ensureContentDirectory(workspace);
    WorkspaceRecord saved = repository.save(workspace, principal.userId(), WorkspaceRole.WORKSPACE_OWNER);
    auditService.record(saved.workspaceId(), null, "WORKSPACE_CREATED", "Workspace created");
    return saved;
  }

  public List<WorkspaceRecord> listVisibleWorkspaces() {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    return repository.findVisibleTo(principal.userId(), principal.teamId());
  }

  public WorkspaceRecord getWorkspace(String workspaceId) {
    WorkspaceRecord workspace = repository.findById(workspaceId)
        .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    requireRole(workspace, WorkspaceRole.WORKSPACE_VIEWER);
    return workspace;
  }

  public List<WorkspaceMemberRecord> listMembers(String workspaceId) {
    WorkspaceRecord workspace = requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_VIEWER);
    return repository.listMembers(workspace.workspaceId());
  }

  public WorkspaceMemberRecord grantMember(
      String workspaceId,
      String userId,
      String teamId,
      WorkspaceRole role
  ) {
    WorkspaceRecord workspace = requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedUserId = normalizeMemberValue(userId, "Workspace member user id is required");
    String normalizedTeamId = normalizeMemberValue(teamId, "Workspace member team id is required");
    WorkspaceRole normalizedRole = normalizeMemberRole(role);
    if (!workspace.teamId().equals(normalizedTeamId)) {
      throw new IllegalArgumentException("Workspace member team id must match the workspace team");
    }

    repository.grantAccess(workspace.workspaceId(), normalizedUserId, normalizedTeamId, normalizedRole);
    auditService.record(workspace.workspaceId(), null, "WORKSPACE_MEMBER_GRANTED", "Workspace member granted");
    return new WorkspaceMemberRecord(
        workspace.workspaceId(),
        normalizedUserId,
        normalizedTeamId,
        normalizedRole
    );
  }

  public WorkspaceMemberRecord removeMember(String workspaceId, String userId) {
    WorkspaceRecord workspace = requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedUserId = normalizeMemberValue(userId, "Workspace member user id is required");
    WorkspaceMemberRecord member = repository.findMember(workspace.workspaceId(), normalizedUserId)
        .orElseThrow(() -> new IllegalArgumentException("Workspace member not found"));
    if (member.role() == WorkspaceRole.WORKSPACE_OWNER) {
      throw new IllegalArgumentException("Workspace owner removal is not supported through this API");
    }
    boolean removed = repository.revokeAccess(workspace.workspaceId(), normalizedUserId, member.teamId());
    if (!removed) {
      throw new IllegalArgumentException("Workspace member not found");
    }
    auditService.record(workspace.workspaceId(), null, "WORKSPACE_MEMBER_REMOVED", "Workspace member removed");
    return member;
  }

  public WorkspaceMemberRecord transferOwnership(String workspaceId, String newOwnerUserId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    WorkspaceRecord workspace = requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedNewOwnerUserId = normalizeMemberValue(
        newOwnerUserId,
        "New workspace owner user id is required"
    );
    if (principal.userId().equals(normalizedNewOwnerUserId)) {
      throw new IllegalArgumentException("Workspace owner transfer target must be a different user");
    }

    WorkspaceMemberRecord newOwnerMember = repository.findMember(workspace.workspaceId(), normalizedNewOwnerUserId)
        .orElseThrow(() -> new IllegalArgumentException("Workspace owner transfer target must be an existing member"));
    if (!workspace.teamId().equals(newOwnerMember.teamId())) {
      throw new IllegalArgumentException("Workspace owner transfer target must belong to the workspace team");
    }
    if (newOwnerMember.role() == WorkspaceRole.WORKSPACE_OWNER) {
      throw new IllegalArgumentException("Workspace owner transfer target is already an owner");
    }

    repository.transferOwnership(
        workspace.workspaceId(),
        principal.userId(),
        normalizedNewOwnerUserId,
        workspace.teamId()
    );
    auditService.record(
        workspace.workspaceId(),
        null,
        "WORKSPACE_OWNER_TRANSFERRED",
        "Workspace ownership transferred to %s".formatted(normalizedNewOwnerUserId)
    );
    return new WorkspaceMemberRecord(
        workspace.workspaceId(),
        normalizedNewOwnerUserId,
        workspace.teamId(),
        WorkspaceRole.WORKSPACE_OWNER
    );
  }

  public WorkspaceRecord requireWorkspaceRole(String workspaceId, WorkspaceRole requiredRole) {
    WorkspaceRecord workspace = repository.findById(workspaceId)
        .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    requireRole(workspace, requiredRole);
    return workspace;
  }

  public Path resolveContentPath(String workspaceId, String relativePath) {
    WorkspaceRecord workspace = getWorkspace(workspaceId);
    Path inputPath = Path.of(relativePath == null ? "" : relativePath);
    if (inputPath.isAbsolute()) {
      throw new InvalidWorkspacePathException(relativePath);
    }

    Path contentRoot = contentRoot(workspace);
    Path resolved = contentRoot.resolve(inputPath).normalize();
    if (!resolved.startsWith(contentRoot)) {
      throw new InvalidWorkspacePathException(relativePath);
    }
    return resolved;
  }

  Path contentRoot(WorkspaceRecord workspace) {
    Path dataRoot = properties.dataRoot();
    Path contentRoot = dataRoot.resolve(workspace.serverStorageRef()).normalize();
    if (!contentRoot.startsWith(dataRoot)) {
      throw new InvalidWorkspacePathException(workspace.serverStorageRef());
    }
    return contentRoot;
  }

  private void requireRole(WorkspaceRecord workspace, WorkspaceRole requiredRole) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    WorkspaceRole actualRole = repository.findRole(workspace.workspaceId(), principal.userId(), principal.teamId())
        .orElse(null);
    if (!hasRequiredRole(actualRole, requiredRole)) {
      throw new WorkspaceForbiddenException(workspace.workspaceId());
    }
  }

  private void ensureContentDirectory(WorkspaceRecord workspace) {
    try {
      java.nio.file.Files.createDirectories(contentRoot(workspace));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static String normalizeName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Workspace name is required");
    }
    return name.trim();
  }

  private static String normalizeDefaultBranch(String defaultBranch) {
    if (defaultBranch == null || defaultBranch.trim().isEmpty()) {
      return "main";
    }
    return defaultBranch.trim();
  }

  private static String normalizeMemberValue(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static WorkspaceRole normalizeMemberRole(WorkspaceRole role) {
    if (role == null) {
      throw new IllegalArgumentException("Workspace member role is required");
    }
    if (role == WorkspaceRole.WORKSPACE_OWNER) {
      throw new IllegalArgumentException("Workspace owner grants are not supported through this API");
    }
    return role;
  }

  private static boolean hasRequiredRole(WorkspaceRole actualRole, WorkspaceRole requiredRole) {
    if (actualRole == null) {
      return false;
    }
    return roleRank(actualRole) >= roleRank(requiredRole);
  }

  private static int roleRank(WorkspaceRole role) {
    return switch (role) {
      case WORKSPACE_VIEWER -> 1;
      case WORKSPACE_EDITOR -> 2;
      case WORKSPACE_OWNER -> 3;
    };
  }
}
