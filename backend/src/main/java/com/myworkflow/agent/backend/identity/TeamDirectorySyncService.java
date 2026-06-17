package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.workspace.WorkspaceRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TeamDirectorySyncService {

  private static final Pattern SAFE_SOURCE_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,80}");
  private static final Pattern SAFE_USER_ID_PATTERN = Pattern.compile("[A-Za-z0-9._@:-]{1,120}");

  private final PrincipalProvider principalProvider;
  private final WorkspaceRepository workspaceRepository;

  public TeamDirectorySyncService(
      PrincipalProvider principalProvider,
      WorkspaceRepository workspaceRepository
  ) {
    this.principalProvider = principalProvider;
    this.workspaceRepository = workspaceRepository;
  }

  public DirectorySyncResult syncCurrentTeamDirectory(
      String teamId,
      String source,
      boolean disableMissing,
      List<DirectorySyncMemberInput> members
  ) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    requireCurrentTeamAdmin(normalizedTeamId, principal);
    String normalizedSource = normalizeSource(source);
    List<DirectorySyncMemberInput> normalizedMembers = normalizeMembers(members);

    Set<String> importedUserIds = new LinkedHashSet<>();
    for (DirectorySyncMemberInput member : normalizedMembers) {
      importedUserIds.add(member.userId());
      workspaceRepository.upsertTeamMember(
          normalizedTeamId,
          member.userId(),
          member.displayName(),
          member.role()
      );
    }

    int disabledCount = 0;
    if (disableMissing) {
      for (TeamMemberRecord knownMember : workspaceRepository.listKnownTeamMembers(normalizedTeamId)) {
        if (knownMember.status() == TeamMemberStatus.ACTIVE
            && !knownMember.userId().equals(principal.userId())
            && !importedUserIds.contains(knownMember.userId())) {
          workspaceRepository.disableTeamMember(normalizedTeamId, knownMember.userId());
          disabledCount++;
        }
      }
    }

    List<TeamMemberRecord> syncedMembers = workspaceRepository.listKnownTeamMembers(normalizedTeamId).stream()
        .sorted(Comparator.comparing(TeamMemberRecord::userId))
        .toList();
    return new DirectorySyncResult(
        normalizedTeamId,
        normalizedSource,
        importedUserIds.size(),
        disabledCount,
        syncedMembers
    );
  }

  private static List<DirectorySyncMemberInput> normalizeMembers(List<DirectorySyncMemberInput> members) {
    if (members == null || members.isEmpty()) {
      throw new IllegalArgumentException("Directory sync members are required");
    }
    return members.stream()
        .map((member) -> new DirectorySyncMemberInput(
            normalizeUserId(member == null ? null : member.userId()),
            normalizeDisplayName(member == null ? null : member.displayName(), member == null ? null : member.userId()),
            normalizeRole(member == null ? null : member.role())
        ))
        .toList();
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

  private static String normalizeSource(String source) {
    if (source == null || source.trim().isEmpty()) {
      throw new IllegalArgumentException("Directory sync source is required");
    }
    String normalizedSource = source.trim();
    if (!SAFE_SOURCE_PATTERN.matcher(normalizedSource).matches()) {
      throw new IllegalArgumentException("Directory sync source is invalid");
    }
    return normalizedSource;
  }

  private static String normalizeUserId(String userId) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("Directory sync user id is required");
    }
    String normalizedUserId = userId.trim();
    if (!SAFE_USER_ID_PATTERN.matcher(normalizedUserId).matches()) {
      throw new IllegalArgumentException("Directory sync user id is invalid");
    }
    return normalizedUserId;
  }

  private static String normalizeDisplayName(String displayName, String fallbackUserId) {
    if (displayName == null || displayName.trim().isEmpty()) {
      return normalizeUserId(fallbackUserId);
    }
    return displayName.trim();
  }

  private static TeamRole normalizeRole(TeamRole role) {
    if (role == null) {
      throw new IllegalArgumentException("Directory sync role is required");
    }
    return role;
  }

  public record DirectorySyncMemberInput(
      String userId,
      String displayName,
      TeamRole role
  ) {
  }

  public record DirectorySyncResult(
      String teamId,
      String source,
      int importedCount,
      int disabledCount,
      List<TeamMemberRecord> members
  ) {
  }
}
