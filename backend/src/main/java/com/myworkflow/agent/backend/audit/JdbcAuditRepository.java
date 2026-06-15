package com.myworkflow.agent.backend.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcAuditRepository implements AuditRepository {

  private final JdbcTemplate jdbcTemplate;
  private final RowMapper<AuditEventRecord> rowMapper = this::mapEvent;

  public JdbcAuditRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public AuditEventRecord append(
      String actorUserId,
      String teamId,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      Instant createdAt
  ) {
    AuditEventRecord event = new AuditEventRecord(
        "aud_" + UUID.randomUUID().toString().replace("-", ""),
        actorUserId,
        teamId,
        workspaceId,
        runId,
        eventType,
        message,
        createdAt
    );
    jdbcTemplate.update(
        """
            INSERT INTO audit_events (
              id, actor_user_id, team_id, workspace_id, run_id, event_type, message, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
        event.auditEventId(),
        event.actorUserId(),
        event.teamId(),
        event.workspaceId(),
        event.runId(),
        event.eventType(),
        event.message(),
        Timestamp.from(event.createdAt())
    );
    return event;
  }

  @Override
  public List<AuditEventRecord> findByRunId(String runId) {
    return jdbcTemplate.query(
        """
            SELECT id, actor_user_id, team_id, workspace_id, run_id, event_type, message, created_at
            FROM audit_events
            WHERE run_id = ?
            ORDER BY created_at ASC, id ASC
            """,
        rowMapper,
        runId
    );
  }

  @Override
  public List<AuditEventRecord> findByWorkspaceId(String workspaceId, AuditEventQuery query) {
    StringBuilder sql = new StringBuilder("""
        SELECT id, actor_user_id, team_id, workspace_id, run_id, event_type, message, created_at
        FROM audit_events
        WHERE workspace_id = ?
        """);
    List<Object> parameters = new ArrayList<>();
    parameters.add(workspaceId);
    if (query.eventType() != null) {
      sql.append(" AND event_type = ?");
      parameters.add(query.eventType());
    }
    if (query.runId() != null) {
      sql.append(" AND run_id = ?");
      parameters.add(query.runId());
    }
    sql.append(" ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?");
    parameters.add(query.limit());
    parameters.add(query.offset());

    return jdbcTemplate.query(
        sql.toString(),
        rowMapper,
        parameters.toArray()
    );
  }

  private AuditEventRecord mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
    return new AuditEventRecord(
        resultSet.getString("id"),
        resultSet.getString("actor_user_id"),
        resultSet.getString("team_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("run_id"),
        resultSet.getString("event_type"),
        resultSet.getString("message"),
        resultSet.getTimestamp("created_at").toInstant()
    );
  }
}
