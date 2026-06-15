package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.myworkflow.agent.backend.identity.TeamMemberRecord;
import com.myworkflow.agent.backend.identity.TeamRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class WorkspaceRepositoryContract {

  private WorkspaceRepositoryContract() {
  }

  static void assertRepositoryContract(WorkspaceRepository repository) {
    String unique = UUID.randomUUID().toString().replace("-", "");
    String workspaceId = "ws_contract_" + unique;
    String teamId = "team_contract_" + unique;
    String ownerUserId = "user_contract_" + unique;
    WorkspaceRecord workspace = new WorkspaceRecord(
        workspaceId,
        teamId,
        "Contract Workspace",
        "teams/%s/workspaces/%s/content".formatted(teamId, workspaceId),
        "main",
        WorkspaceStatus.ACTIVE,
        Instant.parse("2026-06-14T00:00:00Z")
    );

    WorkspaceRecord saved = repository.save(
        workspace,
        ownerUserId,
        WorkspaceRole.WORKSPACE_OWNER
    );

    assertThat(saved.workspaceId()).isEqualTo(workspaceId);
    assertThat(repository.findById(workspaceId)).contains(saved);
    assertThat(repository.canAccess(workspaceId, ownerUserId, teamId)).isTrue();
    assertThat(repository.canAccess(workspaceId, "other-user", teamId)).isFalse();
    assertThat(repository.canAccess(workspaceId, ownerUserId, "other-team")).isFalse();

    List<WorkspaceRecord> visible = repository.findVisibleTo(ownerUserId, teamId);
    assertThat(visible)
        .extracting(WorkspaceRecord::workspaceId)
        .contains(workspaceId);
    assertThat(repository.findVisibleTo("other-user", teamId)).isEmpty();

    String viewerUserId = "viewer_contract_" + unique;
    repository.grantAccess(workspaceId, viewerUserId, teamId, WorkspaceRole.WORKSPACE_VIEWER);

    assertThat(repository.listMembers(workspaceId))
        .extracting(
            WorkspaceMemberRecord::workspaceId,
            WorkspaceMemberRecord::userId,
            WorkspaceMemberRecord::teamId,
            WorkspaceMemberRecord::role
        )
        .contains(
            tuple(workspaceId, ownerUserId, teamId, WorkspaceRole.WORKSPACE_OWNER),
            tuple(workspaceId, viewerUserId, teamId, WorkspaceRole.WORKSPACE_VIEWER)
        );
    assertThat(repository.findVisibleTo(viewerUserId, teamId))
        .extracting(WorkspaceRecord::workspaceId)
        .contains(workspaceId);
    assertThat(repository.findVisibleTo(viewerUserId, "other-team")).isEmpty();
    assertThat(repository.listKnownTeamMembers(teamId))
        .extracting(
            TeamMemberRecord::teamId,
            TeamMemberRecord::userId,
            TeamMemberRecord::role
        )
        .contains(
            tuple(teamId, ownerUserId, TeamRole.TEAM_ADMIN),
            tuple(teamId, viewerUserId, TeamRole.TEAM_MEMBER)
        );
    assertThat(repository.findMember(workspaceId, viewerUserId))
        .contains(new WorkspaceMemberRecord(
            workspaceId,
            viewerUserId,
            teamId,
            WorkspaceRole.WORKSPACE_VIEWER
        ));
    repository.transferOwnership(workspaceId, ownerUserId, viewerUserId, teamId);
    assertThat(repository.findRole(workspaceId, ownerUserId, teamId))
        .contains(WorkspaceRole.WORKSPACE_EDITOR);
    assertThat(repository.findRole(workspaceId, viewerUserId, teamId))
        .contains(WorkspaceRole.WORKSPACE_OWNER);
    assertThat(repository.findVisibleTo(ownerUserId, teamId))
        .extracting(WorkspaceRecord::workspaceId)
        .contains(workspaceId);
    assertThat(repository.findVisibleTo(viewerUserId, teamId))
        .extracting(WorkspaceRecord::workspaceId)
        .contains(workspaceId);
    assertThat(repository.revokeAccess(workspaceId, viewerUserId, teamId)).isTrue();
    assertThat(repository.findMember(workspaceId, viewerUserId)).isEmpty();
    assertThat(repository.canAccess(workspaceId, viewerUserId, teamId)).isFalse();
    assertThat(repository.findVisibleTo(viewerUserId, teamId)).isEmpty();
    assertThat(repository.listKnownTeamMembers(teamId))
        .extracting(TeamMemberRecord::userId)
        .contains(viewerUserId);
    assertThat(repository.revokeAccess(workspaceId, viewerUserId, teamId)).isFalse();
    assertThatThrownBy(() -> repository.grantAccess(
        workspaceId,
        "outside_team_contract_" + unique,
        "other-team",
        WorkspaceRole.WORKSPACE_VIEWER
    ))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
