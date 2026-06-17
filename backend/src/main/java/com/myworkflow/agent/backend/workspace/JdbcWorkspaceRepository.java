package com.myworkflow.agent.backend.workspace;

import com.myworkflow.agent.backend.identity.TeamMemberRecord;
import com.myworkflow.agent.backend.identity.TeamMemberStatus;
import com.myworkflow.agent.backend.identity.TeamRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcWorkspaceRepository implements WorkspaceRepository {

  private static final RowMapper<WorkspaceRecord> WORKSPACE_ROW_MAPPER =
      JdbcWorkspaceRepository::mapWorkspace;
  private static final RowMapper<WorkspaceMemberRecord> WORKSPACE_MEMBER_ROW_MAPPER =
      JdbcWorkspaceRepository::mapWorkspaceMember;
  private static final RowMapper<TeamMemberRecord> TEAM_MEMBER_ROW_MAPPER =
      JdbcWorkspaceRepository::mapTeamMember;

  private final JdbcTemplate jdbcTemplate;

  public JdbcWorkspaceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public WorkspaceRecord save(WorkspaceRecord workspace, String ownerUserId, WorkspaceRole role) {
    upsertUser(ownerUserId);
    upsertTeam(workspace.teamId());
    upsertTeamMembership(workspace.teamId(), ownerUserId, TeamRole.TEAM_ADMIN, TeamMemberStatus.ACTIVE);
    upsertWorkspace(workspace);
    upsertWorkspaceMember(workspace.workspaceId(), ownerUserId, role);
    return workspace;
  }

  @Override
  public Optional<WorkspaceRecord> findById(String workspaceId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id, team_id, name, server_storage_ref, default_branch, status, created_at
              FROM workspaces
              WHERE id = ?
              """,
          WORKSPACE_ROW_MAPPER,
          workspaceId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public List<WorkspaceRecord> findVisibleTo(String userId, String teamId) {
    return jdbcTemplate.query(
        """
            SELECT w.id, w.team_id, w.name, w.server_storage_ref, w.default_branch, w.status, w.created_at
            FROM workspaces w
            JOIN workspace_members wm ON wm.workspace_id = w.id
            JOIN team_memberships tm ON tm.team_id = w.team_id AND tm.user_id = wm.user_id
            WHERE wm.user_id = ?
              AND w.team_id = ?
              AND tm.status = 'ACTIVE'
            ORDER BY w.created_at ASC, w.id ASC
            """,
        WORKSPACE_ROW_MAPPER,
        userId,
        teamId
    );
  }

  @Override
  public List<TeamMemberRecord> listKnownTeamMembers(String teamId) {
    return jdbcTemplate.query(
        """
            SELECT tm.team_id, tm.user_id, u.display_name, tm.role, tm.status
            FROM team_memberships tm
            JOIN users u ON u.id = tm.user_id
            WHERE tm.team_id = ?
            ORDER BY tm.user_id ASC
            """,
        TEAM_MEMBER_ROW_MAPPER,
        teamId
    );
  }

  @Override
  public Optional<TeamMemberRecord> findTeamMember(String teamId, String userId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT tm.team_id, tm.user_id, u.display_name, tm.role, tm.status
              FROM team_memberships tm
              JOIN users u ON u.id = tm.user_id
              WHERE tm.team_id = ?
                AND tm.user_id = ?
              """,
          TEAM_MEMBER_ROW_MAPPER,
          teamId,
          userId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public TeamMemberRecord upsertTeamMember(String teamId, String userId, String displayName, TeamRole role) {
    upsertUser(userId, displayName);
    upsertTeam(teamId);
    upsertTeamMembership(teamId, userId, role, TeamMemberStatus.ACTIVE);
    return findTeamMember(teamId, userId)
        .orElseThrow(() -> new IllegalArgumentException("Team member not found"));
  }

  @Override
  public TeamMemberRecord disableTeamMember(String teamId, String userId) {
    int updated = jdbcTemplate.update(
        """
            UPDATE team_memberships
            SET status = ?
            WHERE team_id = ?
              AND user_id = ?
            """,
        TeamMemberStatus.DISABLED.name(),
        teamId,
        userId
    );
    if (updated == 0) {
      throw new IllegalArgumentException("Team member not found");
    }
    return findTeamMember(teamId, userId)
        .orElseThrow(() -> new IllegalArgumentException("Team member not found"));
  }

  @Override
  public List<WorkspaceMemberRecord> listMembers(String workspaceId) {
    return jdbcTemplate.query(
        """
            SELECT wm.workspace_id, wm.user_id, w.team_id, wm.role
            FROM workspace_members wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.workspace_id = ?
            ORDER BY wm.user_id ASC
            """,
        WORKSPACE_MEMBER_ROW_MAPPER,
        workspaceId
    );
  }

  @Override
  public Optional<WorkspaceMemberRecord> findMember(String workspaceId, String userId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT wm.workspace_id, wm.user_id, w.team_id, wm.role
              FROM workspace_members wm
              JOIN workspaces w ON w.id = wm.workspace_id
              WHERE wm.workspace_id = ?
                AND wm.user_id = ?
              """,
          WORKSPACE_MEMBER_ROW_MAPPER,
          workspaceId,
          userId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public boolean canAccess(String workspaceId, String userId, String teamId) {
    return findRole(workspaceId, userId, teamId).isPresent();
  }

  @Override
  public Optional<WorkspaceRole> findRole(String workspaceId, String userId, String teamId) {
    try {
      String role = jdbcTemplate.queryForObject(
          """
              SELECT wm.role
              FROM workspace_members wm
              JOIN workspaces w ON w.id = wm.workspace_id
              JOIN team_memberships tm ON tm.team_id = w.team_id AND tm.user_id = wm.user_id
              WHERE wm.workspace_id = ?
                AND wm.user_id = ?
                AND w.team_id = ?
                AND tm.status = 'ACTIVE'
              """,
          String.class,
          workspaceId,
          userId,
          teamId
      );
      return Optional.of(WorkspaceRole.valueOf(role));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public void grantAccess(String workspaceId, String userId, String teamId, WorkspaceRole role) {
    WorkspaceRecord workspace = findById(workspaceId)
        .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    if (!workspace.teamId().equals(teamId)) {
      throw new IllegalArgumentException("Workspace member team id must match the workspace team");
    }
    ensureTeamMemberCanReceiveWorkspaceGrant(teamId, userId);
    upsertUser(userId);
    upsertTeam(teamId);
    upsertTeamMembership(teamId, userId, TeamRole.TEAM_MEMBER, TeamMemberStatus.ACTIVE);
    upsertWorkspaceMember(workspaceId, userId, role);
  }

  @Override
  public boolean revokeAccess(String workspaceId, String userId, String teamId) {
    WorkspaceRecord workspace = findById(workspaceId)
        .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    if (!workspace.teamId().equals(teamId)) {
      throw new IllegalArgumentException("Workspace member team id must match the workspace team");
    }
    return jdbcTemplate.update(
        """
            DELETE FROM workspace_members
            WHERE workspace_id = ?
              AND user_id = ?
            """,
        workspaceId,
        userId
    ) > 0;
  }

  @Override
  public void transferOwnership(
      String workspaceId,
      String currentOwnerUserId,
      String newOwnerUserId,
      String teamId
  ) {
    WorkspaceRecord workspace = findById(workspaceId)
        .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    if (!workspace.teamId().equals(teamId)) {
      throw new IllegalArgumentException("Workspace owner transfer team id must match the workspace team");
    }
    int updated = jdbcTemplate.update(
        """
            UPDATE workspace_members
            SET role = CASE
              WHEN user_id = ? THEN ?
              WHEN user_id = ? THEN ?
              ELSE role
            END
            WHERE workspace_id = ?
              AND user_id IN (?, ?)
            """,
        currentOwnerUserId,
        WorkspaceRole.WORKSPACE_EDITOR.name(),
        newOwnerUserId,
        WorkspaceRole.WORKSPACE_OWNER.name(),
        workspaceId,
        currentOwnerUserId,
        newOwnerUserId
    );
    if (updated != 2) {
      throw new IllegalArgumentException("Workspace owner transfer requires existing owner and target members");
    }
  }

  private void upsertUser(String userId) {
    upsertUser(userId, userId);
  }

  private void upsertUser(String userId, String displayName) {
    jdbcTemplate.update(
        """
            INSERT INTO users (id, external_subject, display_name, status)
            VALUES (?, ?, ?, 'ACTIVE')
            ON DUPLICATE KEY UPDATE
              display_name = VALUES(display_name),
              status = VALUES(status)
            """,
        userId,
        userId,
        displayName
    );
  }

  private void upsertTeam(String teamId) {
    jdbcTemplate.update(
        """
            INSERT INTO teams (id, name, status)
            VALUES (?, ?, 'ACTIVE')
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              status = VALUES(status)
            """,
        teamId,
        teamId
    );
  }

  private void upsertTeamMembership(String teamId, String userId, TeamRole role, TeamMemberStatus status) {
    jdbcTemplate.update(
        """
            INSERT INTO team_memberships (team_id, user_id, role, status)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              role = IF(role = 'TEAM_ADMIN', 'TEAM_ADMIN', VALUES(role)),
              status = VALUES(status)
            """,
        teamId,
        userId,
        role.name(),
        status.name()
    );
  }

  private void ensureTeamMemberCanReceiveWorkspaceGrant(String teamId, String userId) {
    findTeamMember(teamId, userId)
        .filter((member) -> member.status() == TeamMemberStatus.DISABLED)
        .ifPresent((member) -> {
          throw new IllegalArgumentException("Team member is disabled");
        });
  }

  private void upsertWorkspace(WorkspaceRecord workspace) {
    jdbcTemplate.update(
        """
            INSERT INTO workspaces (
              id, team_id, name, storage_mode, server_storage_ref, default_branch, status, created_at
            )
            VALUES (?, ?, ?, 'SERVER_HOSTED', ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              storage_mode = VALUES(storage_mode),
              server_storage_ref = VALUES(server_storage_ref),
              default_branch = VALUES(default_branch),
              status = VALUES(status)
            """,
        workspace.workspaceId(),
        workspace.teamId(),
        workspace.name(),
        workspace.serverStorageRef(),
        workspace.defaultBranch(),
        workspace.status().name(),
        Timestamp.from(workspace.createdAt())
    );
  }

  private void upsertWorkspaceMember(String workspaceId, String userId, WorkspaceRole role) {
    jdbcTemplate.update(
        """
            INSERT INTO workspace_members (workspace_id, user_id, role)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
              role = VALUES(role)
            """,
        workspaceId,
        userId,
        role.name()
    );
  }

  private static WorkspaceRecord mapWorkspace(ResultSet resultSet, int rowNum) throws SQLException {
    return new WorkspaceRecord(
        resultSet.getString("id"),
        resultSet.getString("team_id"),
        resultSet.getString("name"),
        resultSet.getString("server_storage_ref"),
        resultSet.getString("default_branch"),
        WorkspaceStatus.valueOf(resultSet.getString("status")),
        resultSet.getTimestamp("created_at").toInstant()
    );
  }

  private static WorkspaceMemberRecord mapWorkspaceMember(ResultSet resultSet, int rowNum) throws SQLException {
    return new WorkspaceMemberRecord(
        resultSet.getString("workspace_id"),
        resultSet.getString("user_id"),
        resultSet.getString("team_id"),
        WorkspaceRole.valueOf(resultSet.getString("role"))
    );
  }

  private static TeamMemberRecord mapTeamMember(ResultSet resultSet, int rowNum) throws SQLException {
    return new TeamMemberRecord(
        resultSet.getString("team_id"),
        resultSet.getString("user_id"),
        resultSet.getString("display_name"),
        TeamRole.valueOf(resultSet.getString("role")),
        TeamMemberStatus.valueOf(resultSet.getString("status"))
    );
  }
}
