package com.myworkflow.agent.backend.workspace;

import com.myworkflow.agent.backend.identity.TeamMemberRecord;
import com.myworkflow.agent.backend.identity.TeamRole;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

  WorkspaceRecord save(WorkspaceRecord workspace, String ownerUserId, WorkspaceRole role);

  Optional<WorkspaceRecord> findById(String workspaceId);

  List<WorkspaceRecord> findVisibleTo(String userId, String teamId);

  List<TeamMemberRecord> listKnownTeamMembers(String teamId);

  Optional<TeamMemberRecord> findTeamMember(String teamId, String userId);

  TeamMemberRecord upsertTeamMember(String teamId, String userId, String displayName, TeamRole role);

  TeamMemberRecord disableTeamMember(String teamId, String userId);

  List<WorkspaceMemberRecord> listMembers(String workspaceId);

  Optional<WorkspaceMemberRecord> findMember(String workspaceId, String userId);

  boolean canAccess(String workspaceId, String userId, String teamId);

  Optional<WorkspaceRole> findRole(String workspaceId, String userId, String teamId);

  void grantAccess(String workspaceId, String userId, String teamId, WorkspaceRole role);

  boolean revokeAccess(String workspaceId, String userId, String teamId);

  void transferOwnership(String workspaceId, String currentOwnerUserId, String newOwnerUserId, String teamId);
}
