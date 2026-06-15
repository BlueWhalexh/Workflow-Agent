package com.myworkflow.agent.backend.workspace;

import com.myworkflow.agent.backend.identity.TeamMemberRecord;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

  WorkspaceRecord save(WorkspaceRecord workspace, String ownerUserId, WorkspaceRole role);

  Optional<WorkspaceRecord> findById(String workspaceId);

  List<WorkspaceRecord> findVisibleTo(String userId, String teamId);

  List<TeamMemberRecord> listKnownTeamMembers(String teamId);

  List<WorkspaceMemberRecord> listMembers(String workspaceId);

  Optional<WorkspaceMemberRecord> findMember(String workspaceId, String userId);

  boolean canAccess(String workspaceId, String userId, String teamId);

  Optional<WorkspaceRole> findRole(String workspaceId, String userId, String teamId);

  void grantAccess(String workspaceId, String userId, String teamId, WorkspaceRole role);

  boolean revokeAccess(String workspaceId, String userId, String teamId);

  void transferOwnership(String workspaceId, String currentOwnerUserId, String newOwnerUserId, String teamId);
}
