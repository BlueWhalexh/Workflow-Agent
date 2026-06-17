package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TeamInviteService {

  private final PrincipalProvider principalProvider;
  private final TeamInviteRepository inviteRepository;
  private final WorkspaceRepository workspaceRepository;

  public TeamInviteService(
      PrincipalProvider principalProvider,
      TeamInviteRepository inviteRepository,
      WorkspaceRepository workspaceRepository
  ) {
    this.principalProvider = principalProvider;
    this.inviteRepository = inviteRepository;
    this.workspaceRepository = workspaceRepository;
  }

  public TeamInviteRecord createCurrentTeamInvite(
      String teamId,
      String inviteeUserId,
      String displayName,
      TeamRole role
  ) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    requireCurrentTeamAdmin(normalizedTeamId, principal);
    String normalizedInviteeUserId = normalizeUserId(inviteeUserId);
    if (principal.userId().equals(normalizedInviteeUserId)) {
      throw new IllegalArgumentException("Current team admin cannot invite self");
    }

    Instant now = Instant.now();
    return inviteRepository.create(new TeamInviteRecord(
        "ti_" + UUID.randomUUID().toString().replace("-", ""),
        normalizedTeamId,
        normalizedInviteeUserId,
        normalizeDisplayName(displayName, normalizedInviteeUserId),
        normalizeRole(role),
        TeamInviteStatus.PENDING,
        principal.userId(),
        now,
        now
    ));
  }

  public List<TeamInviteRecord> listCurrentTeamInvites(String teamId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    requireCurrentTeamAdmin(normalizedTeamId, principal);
    return inviteRepository.listByTeam(normalizedTeamId);
  }

  public TeamInviteRecord acceptCurrentTeamInvite(String teamId, String inviteId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    TeamInviteRecord invite = findInvite(normalizedTeamId, inviteId);
    if (!invite.inviteeUserId().equals(principal.userId())) {
      throw new TeamForbiddenException(normalizedTeamId);
    }
    if (invite.status() != TeamInviteStatus.PENDING) {
      throw new IllegalArgumentException("Team invite is not pending");
    }

    TeamInviteRecord accepted = inviteRepository.updateStatus(
        normalizedTeamId,
        normalizeInviteId(inviteId),
        TeamInviteStatus.ACCEPTED
    );
    workspaceRepository.upsertTeamMember(
        normalizedTeamId,
        accepted.inviteeUserId(),
        accepted.displayName(),
        accepted.role()
    );
    return accepted;
  }

  public TeamInviteRecord revokeCurrentTeamInvite(String teamId, String inviteId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = requireCurrentTeam(teamId, principal);
    requireCurrentTeamAdmin(normalizedTeamId, principal);
    TeamInviteRecord invite = findInvite(normalizedTeamId, inviteId);
    if (invite.status() != TeamInviteStatus.PENDING) {
      throw new IllegalArgumentException("Team invite is not pending");
    }
    return inviteRepository.updateStatus(
        normalizedTeamId,
        normalizeInviteId(inviteId),
        TeamInviteStatus.REVOKED
    );
  }

  private TeamInviteRecord findInvite(String teamId, String inviteId) {
    return inviteRepository.findById(teamId, normalizeInviteId(inviteId))
        .orElseThrow(() -> new IllegalArgumentException("Team invite not found"));
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
        .filter(member -> member.role() == TeamRole.TEAM_ADMIN)
        .filter(member -> member.status() == TeamMemberStatus.ACTIVE)
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

  private static String normalizeInviteId(String inviteId) {
    if (inviteId == null || inviteId.trim().isEmpty()) {
      throw new IllegalArgumentException("Team invite id is required");
    }
    return inviteId.trim();
  }

  private static String normalizeUserId(String userId) {
    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("Team invitee user id is required");
    }
    return userId.trim();
  }

  private static String normalizeDisplayName(String displayName, String fallbackUserId) {
    if (displayName == null || displayName.trim().isEmpty()) {
      return fallbackUserId;
    }
    return displayName.trim();
  }

  private static TeamRole normalizeRole(TeamRole role) {
    if (role == null) {
      throw new IllegalArgumentException("Team invite role is required");
    }
    return role;
  }
}
