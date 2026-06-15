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
    String normalizedTeamId = normalizeTeamId(teamId);
    if (!principal.teamId().equals(normalizedTeamId)) {
      throw new TeamForbiddenException(normalizedTeamId);
    }

    List<TeamMemberRecord> members = new ArrayList<>(
        workspaceRepository.listKnownTeamMembers(normalizedTeamId)
    );
    boolean hasCurrentPrincipal = members.stream()
        .anyMatch((member) -> member.userId().equals(principal.userId()));
    if (!hasCurrentPrincipal) {
      members.add(new TeamMemberRecord(
          normalizedTeamId,
          principal.userId(),
          TeamRole.TEAM_ADMIN
      ));
    }

    return members.stream()
        .sorted(Comparator.comparing(TeamMemberRecord::userId))
        .toList();
  }

  private static String normalizeTeamId(String teamId) {
    if (teamId == null || teamId.trim().isEmpty()) {
      throw new IllegalArgumentException("Team id is required");
    }
    return teamId.trim();
  }
}
