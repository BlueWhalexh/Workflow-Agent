package com.myworkflow.agent.backend.identity;

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
public class JdbcTeamInviteRepository implements TeamInviteRepository {

  private static final RowMapper<TeamInviteRecord> ROW_MAPPER = JdbcTeamInviteRepository::mapInvite;

  private final JdbcTemplate jdbcTemplate;

  public JdbcTeamInviteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public TeamInviteRecord create(TeamInviteRecord invite) {
    jdbcTemplate.update(
        """
            INSERT INTO team_invites (
              id,
              team_id,
              invitee_user_id,
              display_name,
              role,
              status,
              created_by_user_id,
              created_at,
              updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        invite.id(),
        invite.teamId(),
        invite.inviteeUserId(),
        invite.displayName(),
        invite.role().name(),
        invite.status().name(),
        invite.createdByUserId(),
        Timestamp.from(invite.createdAt()),
        Timestamp.from(invite.updatedAt())
    );
    return invite;
  }

  @Override
  public List<TeamInviteRecord> listByTeam(String teamId) {
    return jdbcTemplate.query(
        """
            SELECT id, team_id, invitee_user_id, display_name, role, status, created_by_user_id, created_at, updated_at
            FROM team_invites
            WHERE team_id = ?
            ORDER BY created_at ASC, id ASC
            """,
        ROW_MAPPER,
        teamId
    );
  }

  @Override
  public Optional<TeamInviteRecord> findById(String teamId, String inviteId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id, team_id, invitee_user_id, display_name, role, status, created_by_user_id, created_at, updated_at
              FROM team_invites
              WHERE team_id = ?
                AND id = ?
              """,
          ROW_MAPPER,
          teamId,
          inviteId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public TeamInviteRecord updateStatus(String teamId, String inviteId, TeamInviteStatus status) {
    int updated = jdbcTemplate.update(
        """
            UPDATE team_invites
            SET status = ?,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE team_id = ?
              AND id = ?
            """,
        status.name(),
        teamId,
        inviteId
    );
    if (updated == 0) {
      throw new IllegalArgumentException("Team invite not found");
    }
    return findById(teamId, inviteId)
        .orElseThrow(() -> new IllegalArgumentException("Team invite not found"));
  }

  private static TeamInviteRecord mapInvite(ResultSet resultSet, int rowNum) throws SQLException {
    return new TeamInviteRecord(
        resultSet.getString("id"),
        resultSet.getString("team_id"),
        resultSet.getString("invitee_user_id"),
        resultSet.getString("display_name"),
        TeamRole.valueOf(resultSet.getString("role")),
        TeamInviteStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("created_by_user_id"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant()
    );
  }
}
