package com.myworkflow.agent.backend.workspace;

import com.myworkflow.agent.backend.identity.TeamMemberRecord;
import com.myworkflow.agent.backend.identity.TeamRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryWorkspaceRepository implements WorkspaceRepository {

  private final Map<String, WorkspaceRecord> workspaces = new ConcurrentHashMap<>();
  private final Map<String, List<WorkspaceMembership>> memberships = new ConcurrentHashMap<>();
  private final Map<String, List<TeamMembership>> teamMemberships = new ConcurrentHashMap<>();

  @Override
  public WorkspaceRecord save(WorkspaceRecord workspace, String ownerUserId, WorkspaceRole role) {
    workspaces.put(workspace.workspaceId(), workspace);
    grantTeamMembership(workspace.teamId(), ownerUserId, TeamRole.TEAM_ADMIN);
    memberships.compute(workspace.workspaceId(), (workspaceId, existing) -> {
      List<WorkspaceMembership> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
      updated.add(new WorkspaceMembership(ownerUserId, workspace.teamId(), role));
      return List.copyOf(updated);
    });
    return workspace;
  }

  @Override
  public Optional<WorkspaceRecord> findById(String workspaceId) {
    return Optional.ofNullable(workspaces.get(workspaceId));
  }

  @Override
  public List<WorkspaceRecord> findVisibleTo(String userId, String teamId) {
    return workspaces.values().stream()
        .filter(workspace -> canAccess(workspace.workspaceId(), userId, teamId))
        .sorted(Comparator.comparing(WorkspaceRecord::createdAt))
        .toList();
  }

  @Override
  public List<TeamMemberRecord> listKnownTeamMembers(String teamId) {
    return teamMemberships.getOrDefault(teamId, List.of()).stream()
        .map((membership) -> new TeamMemberRecord(
            teamId,
            membership.userId(),
            membership.role()
        ))
        .sorted(Comparator.comparing(TeamMemberRecord::userId))
        .toList();
  }

  @Override
  public List<WorkspaceMemberRecord> listMembers(String workspaceId) {
    return memberships.getOrDefault(workspaceId, List.of()).stream()
        .map((membership) -> new WorkspaceMemberRecord(
            workspaceId,
            membership.userId(),
            membership.teamId(),
            membership.role()
        ))
        .sorted(Comparator.comparing(WorkspaceMemberRecord::userId))
        .toList();
  }

  @Override
  public Optional<WorkspaceMemberRecord> findMember(String workspaceId, String userId) {
    return memberships.getOrDefault(workspaceId, List.of()).stream()
        .filter(membership -> membership.userId().equals(userId))
        .findFirst()
        .map((membership) -> new WorkspaceMemberRecord(
            workspaceId,
            membership.userId(),
            membership.teamId(),
            membership.role()
        ));
  }

  @Override
  public boolean canAccess(String workspaceId, String userId, String teamId) {
    return findRole(workspaceId, userId, teamId).isPresent();
  }

  @Override
  public Optional<WorkspaceRole> findRole(String workspaceId, String userId, String teamId) {
    return memberships.getOrDefault(workspaceId, List.of()).stream()
        .filter(membership -> membership.userId().equals(userId) && membership.teamId().equals(teamId))
        .map(WorkspaceMembership::role)
        .findFirst();
  }

  @Override
  public void grantAccess(String workspaceId, String userId, String teamId, WorkspaceRole role) {
    WorkspaceRecord workspace = workspaces.get(workspaceId);
    if (workspace == null) {
      throw new WorkspaceNotFoundException(workspaceId);
    }
    if (!workspace.teamId().equals(teamId)) {
      throw new IllegalArgumentException("Workspace member team id must match the workspace team");
    }
    grantTeamMembership(teamId, userId, TeamRole.TEAM_MEMBER);
    memberships.compute(workspaceId, (ignored, existing) -> {
      List<WorkspaceMembership> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
      updated.removeIf(membership -> membership.userId().equals(userId) && membership.teamId().equals(teamId));
      updated.add(new WorkspaceMembership(userId, teamId, role));
      return List.copyOf(updated);
    });
  }

  @Override
  public boolean revokeAccess(String workspaceId, String userId, String teamId) {
    WorkspaceRecord workspace = workspaces.get(workspaceId);
    if (workspace == null) {
      throw new WorkspaceNotFoundException(workspaceId);
    }
    if (!workspace.teamId().equals(teamId)) {
      throw new IllegalArgumentException("Workspace member team id must match the workspace team");
    }

    boolean[] removed = {false};
    memberships.compute(workspaceId, (ignored, existing) -> {
      List<WorkspaceMembership> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
      removed[0] = updated.removeIf(
          membership -> membership.userId().equals(userId) && membership.teamId().equals(teamId)
      );
      return List.copyOf(updated);
    });
    return removed[0];
  }

  @Override
  public void transferOwnership(
      String workspaceId,
      String currentOwnerUserId,
      String newOwnerUserId,
      String teamId
  ) {
    WorkspaceRecord workspace = workspaces.get(workspaceId);
    if (workspace == null) {
      throw new WorkspaceNotFoundException(workspaceId);
    }
    if (!workspace.teamId().equals(teamId)) {
      throw new IllegalArgumentException("Workspace owner transfer team id must match the workspace team");
    }

    memberships.compute(workspaceId, (ignored, existing) -> {
      List<WorkspaceMembership> current = existing == null ? List.of() : existing;
      boolean currentOwnerFound = false;
      boolean newOwnerFound = false;
      List<WorkspaceMembership> updated = new ArrayList<>();
      for (WorkspaceMembership membership : current) {
        if (membership.userId().equals(currentOwnerUserId) && membership.teamId().equals(teamId)) {
          currentOwnerFound = true;
          updated.add(new WorkspaceMembership(currentOwnerUserId, teamId, WorkspaceRole.WORKSPACE_EDITOR));
        } else if (membership.userId().equals(newOwnerUserId) && membership.teamId().equals(teamId)) {
          newOwnerFound = true;
          updated.add(new WorkspaceMembership(newOwnerUserId, teamId, WorkspaceRole.WORKSPACE_OWNER));
        } else {
          updated.add(membership);
        }
      }
      if (!currentOwnerFound || !newOwnerFound) {
        throw new IllegalArgumentException("Workspace owner transfer requires existing owner and target members");
      }
      return List.copyOf(updated);
    });
  }

  private void grantTeamMembership(String teamId, String userId, TeamRole role) {
    teamMemberships.compute(teamId, (ignored, existing) -> {
      List<TeamMembership> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
      TeamRole resolvedRole = role;
      for (TeamMembership membership : updated) {
        if (membership.userId().equals(userId)) {
          resolvedRole = highestTeamRole(membership.role(), role);
        }
      }
      updated.removeIf((membership) -> membership.userId().equals(userId));
      updated.add(new TeamMembership(userId, resolvedRole));
      return List.copyOf(updated);
    });
  }

  private static TeamRole highestTeamRole(TeamRole left, TeamRole right) {
    if (left == TeamRole.TEAM_ADMIN || right == TeamRole.TEAM_ADMIN) {
      return TeamRole.TEAM_ADMIN;
    }
    return TeamRole.TEAM_MEMBER;
  }

  private record WorkspaceMembership(
      String userId,
      String teamId,
      WorkspaceRole role
  ) {
  }

  private record TeamMembership(
      String userId,
      TeamRole role
  ) {
  }
}
