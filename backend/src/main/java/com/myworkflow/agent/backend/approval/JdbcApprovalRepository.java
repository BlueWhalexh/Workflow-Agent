package com.myworkflow.agent.backend.approval;

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
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcApprovalRepository implements ApprovalRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcApprovalRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public ApprovalRequestRecord createPending(
      String runId,
      String workspaceId,
      String requestedByUserId,
      String artifactRef,
      List<String> targetWorkspacePaths,
      Instant now
  ) {
    ApprovalRequestRecord approval = ApprovalRequestFactory.createPending(
        runId,
        workspaceId,
        requestedByUserId,
        artifactRef,
        targetWorkspacePaths,
        now
    );
    jdbcTemplate.update(
        """
            INSERT INTO approval_requests (
              id, run_id, workspace_id, requested_by_user_id, decided_by_user_id,
              decision, status, artifact_ref, target_workspace_paths, created_at, decided_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        approval.approvalId(),
        approval.runId(),
        approval.workspaceId(),
        approval.requestedByUserId(),
        approval.decidedByUserId(),
        approval.decision() == null ? null : approval.decision().name(),
        approval.status().name(),
        approval.artifactRef(),
        writeStringList(approval.targetWorkspacePaths()),
        Timestamp.from(approval.createdAt()),
        null
    );
    return approval;
  }

  @Override
  public List<ApprovalRequestRecord> findByRunId(String runId) {
    return jdbcTemplate.query(
        """
            SELECT id, run_id, workspace_id, requested_by_user_id, decided_by_user_id,
              decision, status, artifact_ref, target_workspace_paths, created_at, decided_at
            FROM approval_requests
            WHERE run_id = ?
            ORDER BY created_at ASC, id ASC
            """,
        this::mapApproval,
        runId
    );
  }

  @Override
  public Optional<ApprovalRequestRecord> findById(String approvalId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id, run_id, workspace_id, requested_by_user_id, decided_by_user_id,
                decision, status, artifact_ref, target_workspace_paths, created_at, decided_at
              FROM approval_requests
              WHERE id = ?
              """,
          this::mapApproval,
          approvalId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public ApprovalRequestRecord decide(
      String approvalId,
      String decidedByUserId,
      ApprovalDecision decision,
      Instant now
  ) {
    ApprovalRequestRecord existing = findById(approvalId)
        .orElseThrow(() -> new ApprovalNotFoundException(approvalId));
    jdbcTemplate.update(
        """
            UPDATE approval_requests
            SET decided_by_user_id = ?, decision = ?, status = ?, decided_at = ?
            WHERE id = ?
            """,
        decidedByUserId,
        decision.name(),
        ApprovalStatus.DECIDED.name(),
        Timestamp.from(now),
        approvalId
    );
    return new ApprovalRequestRecord(
        existing.approvalId(),
        existing.runId(),
        existing.workspaceId(),
        existing.requestedByUserId(),
        decidedByUserId,
        decision,
        ApprovalStatus.DECIDED,
        existing.artifactRef(),
        existing.targetWorkspacePaths(),
        existing.createdAt(),
        now
    );
  }

  private ApprovalRequestRecord mapApproval(ResultSet resultSet, int rowNum) throws SQLException {
    String decision = resultSet.getString("decision");
    Timestamp decidedAt = resultSet.getTimestamp("decided_at");
    return new ApprovalRequestRecord(
        resultSet.getString("id"),
        resultSet.getString("run_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("requested_by_user_id"),
        resultSet.getString("decided_by_user_id"),
        decision == null ? null : ApprovalDecision.valueOf(decision),
        ApprovalStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("artifact_ref"),
        readStringList(resultSet.getString("target_workspace_paths")),
        resultSet.getTimestamp("created_at").toInstant(),
        decidedAt == null ? null : decidedAt.toInstant()
    );
  }

  private String writeStringList(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize approval target paths", exception);
    }
  }

  private List<String> readStringList(String json) {
    try {
      return objectMapper.readValue(json, STRING_LIST);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to parse approval target paths", exception);
    }
  }
}
