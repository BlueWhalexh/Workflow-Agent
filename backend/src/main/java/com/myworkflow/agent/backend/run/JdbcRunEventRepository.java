package com.myworkflow.agent.backend.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcRunEventRepository implements RunEventRepository {

  private final JdbcTemplate jdbcTemplate;
  private final RowMapper<RunEventRecord> rowMapper = this::mapEvent;

  public JdbcRunEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public RunEventRecord append(
      String runId,
      String eventType,
      AgentRunStatus status,
      String message,
      Instant createdAt
  ) {
    RunEventRecord event = new RunEventRecord(
        "evt_" + UUID.randomUUID().toString().replace("-", ""),
        runId,
        eventType,
        status,
        message,
        createdAt
    );
    jdbcTemplate.update(
        """
            INSERT INTO run_events (id, run_id, event_type, status, message, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
        event.eventId(),
        event.runId(),
        event.eventType(),
        event.status().name(),
        event.message(),
        Timestamp.from(event.createdAt())
    );
    return event;
  }

  @Override
  public List<RunEventRecord> findByRunId(String runId) {
    return jdbcTemplate.query(
        """
            SELECT id, run_id, event_type, status, message, created_at
            FROM run_events
            WHERE run_id = ?
            ORDER BY event_sequence ASC
            """,
        rowMapper,
        runId
    );
  }

  private RunEventRecord mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
    return new RunEventRecord(
        resultSet.getString("id"),
        resultSet.getString("run_id"),
        resultSet.getString("event_type"),
        AgentRunStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("message"),
        resultSet.getTimestamp("created_at").toInstant()
    );
  }
}
