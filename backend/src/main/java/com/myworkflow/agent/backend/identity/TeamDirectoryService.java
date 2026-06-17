package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.workspace.WorkspaceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TeamDirectoryService {

  private final PrincipalProvider principalProvider;
  private final WorkspaceRepository workspaceRepository;

  public TeamDirectoryService(
      PrincipalProvider principalProvider,
      WorkspaceRepository workspaceRepository
  ) {
    this.principalProvider = principalProvider;
    this.workspaceRepository = workspaceRepository;
  }

  public List<TeamMemberRecord> listCurrentTeamMembers(String teamId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);

    List<TeamMemberRecord> members = new ArrayList<>(
        workspaceRepository.listKnownTeamMembers(normalizedTeamId)
    );
    boolean hasCurrentPrincipal = members.stream()
        .anyMatch((member) -> member.userId().equals(principal.userId()));
    if (!hasCurrentPrincipal) {
      members.add(new TeamMemberRecord(
          normalizedTeamId,
          principal.userId(),
          principal.displayName(),
          TeamRole.TEAM_ADMIN,
          TeamMemberStatus.ACTIVE
      ));
    }

    return members.stream()
        .sorted(Comparator.comparing(TeamMemberRecord::userId))
        .toList();
  }

  public TeamMemberRecord upsertCurrentTeamMember(
      String teamId,
      String userId,
      String displayName,
      TeamRole role
  ) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    requireCurrentTeamAdmin(normalizedTeamId, principal);
    return workspaceRepository.upsertTeamMember(
        normalizedTeamId,
        normalizeUserId(userId),
        normalizeDisplayName(displayName, userId),
        normalizeRole(role)
    );
  }

  public TeamMemberRecord disableCurrentTeamMember(String teamId, String userId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    requireCurrentTeamAdmin(normalizedTeamId, principal);
    String normalizedUserId = normalizeUserId(userId);
    if (principal.userId().equals(normalizedUserId)) {
      throw new IllegalArgumentException("Current team admin cannot disable self");
    }
    return workspaceRepository.disableTeamMember(normalizedTeamId, normalizedUserId);
  }

  private String requireCurrentTeam(String teamId, BackendPrincipal principal) {
    String normalizedTeamId = normalizeTeamId(teamId);
    if (!principal.teamId().equals(normalizedTeamId)) {
      throw new TeamForbiddenException(normalizedTeamId);
    }
    return normalizedTeamId;
  }

  private void requireCurrentTeamAdmin(String teamId, BackendPrincipal principal) {
    boolean isActiveAdmin = workspaceRepository.findTeamMember(teamId, principal.userId())
        .filter((member) -> member.role() == TeamRole.TEAM_ADMIN)
        .filter((member) -> member.status() == TeamMemberStatus.ACTIVE)
        .isPresent();
    if (!isActiveAdmin) {
      throw new TeamForbiddenException(teamId);
    }
  }

  private static String normalizeTeamId(String teamId) {
    if (teamId == null || teamId.trim().isEmpty()) {
      throw new IllegalArgumentException("Team id is required");
    }
    return teamId.trim();
  }

  private static String normalizeUserId(String userId) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("Team member user id is required");
    }
    return userId.trim();
  }

  private static String normalizeDisplayName(String displayName, String fallbackUserId) {
    if (displayName == null || displayName.trim().isEmpty()) {
      return normalizeUserId(fallbackUserId);
    }
    return displayName.trim();
  }

  private static TeamRole normalizeRole(TeamRole role) {
    if (role == null) {
      throw new IllegalArgumentException("Team member role is required");
    }
    return role;
  }
}
