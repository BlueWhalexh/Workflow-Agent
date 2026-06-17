package com.myworkflow.agent.backend.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class RemoteRunnerRepository {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
  };

  private final JdbcTemplate jdbcTemplate;
  private final RowMapper<RemoteRunnerRecord> rowMapper = this::mapRecord;

  public RemoteRunnerRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public RemoteRunnerRecord save(RemoteRunnerRecord runner) {
    jdbcTemplate.update(
        """
            INSERT INTO remote_runners (
              id,
              workspace_id,
              runner_ref,
              display_name,
              endpoint_url,
              status,
              capabilities,
              last_seen_at,
              lease_owner,
              lease_expires_at,
              created_at,
              updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              display_name = VALUES(display_name),
              endpoint_url = VALUES(endpoint_url),
              status = VALUES(status),
              capabilities = VALUES(capabilities),
              updated_at = VALUES(updated_at)
            """,
        runner.runnerRecordId(),
        runner.workspaceId(),
        runner.runnerRef(),
        runner.displayName(),
        runner.endpointUrl(),
        runner.status().name(),
        writeCapabilities(runner.capabilities()),
        timestampOrNull(runner.lastSeenAt()),
        runner.leaseOwner(),
        timestampOrNull(runner.leaseExpiresAt()),
        Timestamp.from(runner.createdAt()),
        Timestamp.from(runner.updatedAt())
    );
    return findByWorkspaceAndRunnerRef(runner.workspaceId(), runner.runnerRef())
        .orElse(runner);
  }

  public List<RemoteRunnerRecord> listByWorkspace(String workspaceId) {
    return jdbcTemplate.query(
        """
            SELECT id,
                   workspace_id,
                   runner_ref,
                   display_name,
                   endpoint_url,
                   status,
                   capabilities,
                   last_seen_at,
                   lease_owner,
                   lease_expires_at,
                   created_at,
                   updated_at
            FROM remote_runners
            WHERE workspace_id = ?
            ORDER BY runner_ref
            """,
        rowMapper,
        workspaceId
    );
  }

  public Optional<RemoteRunnerRecord> findByWorkspaceAndRunnerRef(String workspaceId, String runnerRef) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id,
                     workspace_id,
                     runner_ref,
                     display_name,
                     endpoint_url,
                     status,
                     capabilities,
                     last_seen_at,
                     lease_owner,
                     lease_expires_at,
                     created_at,
                     updated_at
              FROM remote_runners
              WHERE workspace_id = ?
                AND runner_ref = ?
              LIMIT 1
              """,
          rowMapper,
          workspaceId,
          runnerRef
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  public Optional<RemoteRunnerRecord> recordHeartbeat(String workspaceId, String runnerRef, Instant now) {
    int updated = jdbcTemplate.update(
        """
            UPDATE remote_runners
            SET status = 'ONLINE',
                last_seen_at = ?,
                updated_at = ?
            WHERE workspace_id = ?
              AND runner_ref = ?
            """,
        Timestamp.from(now),
        Timestamp.from(now),
        workspaceId,
        runnerRef
    );
    if (updated == 0) {
      return Optional.empty();
    }
    return findByWorkspaceAndRunnerRef(workspaceId, runnerRef);
  }

  public Optional<RemoteRunnerRecord> claimLease(
      String workspaceId,
      String runnerRef,
      String leaseOwner,
      Instant now,
      Instant leaseExpiresAt
  ) {
    int updated = jdbcTemplate.update(
        """
            UPDATE remote_runners
            SET status = 'LEASED',
                lease_owner = ?,
                lease_expires_at = ?,
                updated_at = ?
            WHERE workspace_id = ?
              AND runner_ref = ?
              AND (lease_expires_at IS NULL OR lease_expires_at <= ? OR lease_owner = ?)
            """,
        leaseOwner,
        Timestamp.from(leaseExpiresAt),
        Timestamp.from(now),
        workspaceId,
        runnerRef,
        Timestamp.from(now),
        leaseOwner
    );
    if (updated == 0) {
      return Optional.empty();
    }
    return findByWorkspaceAndRunnerRef(workspaceId, runnerRef);
  }

  private RemoteRunnerRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
    return new RemoteRunnerRecord(
        resultSet.getString("id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("runner_ref"),
        resultSet.getString("display_name"),
        resultSet.getString("endpoint_url"),
        RemoteRunnerStatus.valueOf(resultSet.getString("status")),
        readCapabilities(resultSet.getString("capabilities")),
        instantOrNull(resultSet.getTimestamp("last_seen_at")),
        resultSet.getString("lease_owner"),
        instantOrNull(resultSet.getTimestamp("lease_expires_at")),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant()
    );
  }

  private static String writeCapabilities(List<String> capabilities) {
    try {
      return OBJECT_MAPPER.writeValueAsString(capabilities == null ? List.of() : capabilities);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Remote runner capabilities are invalid", exception);
    }
  }

  private static List<String> readCapabilities(String value) {
    try {
      return OBJECT_MAPPER.readValue(value, STRING_LIST_TYPE);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Remote runner capabilities are invalid", exception);
    }
  }

  private static Timestamp timestampOrNull(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant instantOrNull(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
