package com.myworkflow.agent.backend.run;

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
public class JdbcAgentRunRepository implements AgentRunRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final RowMapper<AgentRunRecord> runRowMapper = this::mapRun;
  private final RowMapper<AgentJobRecord> jobRowMapper = this::mapJob;

  public JdbcAgentRunRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void create(AgentRunRecord run, AgentJobRecord job) {
    jdbcTemplate.update(
        """
            INSERT INTO agent_runs (
              id, workspace_id, requested_by_user_id, user_message, mode, execute_requested,
              auto_approve, status, output_kind, display_text, requires_approval,
              requires_confirmation, wrote_workspace, artifact_refs, target_workspace_paths,
              error_code, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        run.runId(),
        run.workspaceId(),
        run.requestedByUserId(),
        run.userMessage(),
        run.mode(),
        run.execute(),
        run.autoApprove(),
        run.status().name(),
        run.outputKind(),
        run.displayText(),
        run.requiresApproval(),
        run.requiresConfirmation(),
        run.wroteWorkspace(),
        writeStringList(run.artifactRefs()),
        writeStringList(run.targetWorkspacePaths()),
        run.errorCode(),
        Timestamp.from(run.createdAt()),
        Timestamp.from(run.updatedAt())
    );
    jdbcTemplate.update(
        """
            INSERT INTO agent_jobs (
              id, run_id, status, priority, available_at, locked_by, locked_until,
              attempt_count, max_attempts, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        job.jobId(),
        job.runId(),
        job.status().name(),
        job.priority(),
        Timestamp.from(job.availableAt()),
        job.lockedBy(),
        toTimestamp(job.lockedUntil()),
        job.attemptCount(),
        job.maxAttempts(),
        Timestamp.from(job.createdAt()),
        Timestamp.from(job.updatedAt())
    );
  }

  @Override
  public Optional<AgentRunRecord> findRun(String runId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id, workspace_id, requested_by_user_id, user_message, mode, execute_requested,
                auto_approve, status, output_kind, display_text, requires_approval,
                requires_confirmation, wrote_workspace, artifact_refs, target_workspace_paths,
                error_code, created_at, updated_at
              FROM agent_runs
              WHERE id = ?
              """,
          runRowMapper,
          runId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public List<AgentRunRecord> findRunsByWorkspaceId(String workspaceId) {
    return jdbcTemplate.query(
        """
            SELECT id, workspace_id, requested_by_user_id, user_message, mode, execute_requested,
              auto_approve, status, output_kind, display_text, requires_approval,
              requires_confirmation, wrote_workspace, artifact_refs, target_workspace_paths,
              error_code, created_at, updated_at
            FROM agent_runs
            WHERE workspace_id = ?
            ORDER BY updated_at DESC, id DESC
            """,
        runRowMapper,
        workspaceId
    );
  }

  @Override
  public Optional<AgentJobRecord> findJob(String jobId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id, run_id, status, priority, available_at, locked_by, locked_until,
                attempt_count, max_attempts, created_at, updated_at
              FROM agent_jobs
              WHERE id = ?
              """,
          jobRowMapper,
          jobId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  public List<RunAttemptRecord> findAttempts(String runId) {
    return jdbcTemplate.query(
        """
            SELECT id, run_id, job_id, worker_kind, started_at, finished_at, status, error_code
            FROM run_attempts
            WHERE run_id = ?
            ORDER BY started_at ASC, id ASC
            """,
        this::mapAttempt,
        runId
    );
  }

  @Override
  public void markRunning(String runId, String jobId, String workerKind, Instant now) {
    if (isTerminalRun(runId)) {
      return;
    }
    Integer currentAttemptCount = jdbcTemplate.queryForObject(
        "SELECT attempt_count FROM agent_jobs WHERE id = ?",
        Integer.class,
        jobId
    );
    int nextAttemptCount = (currentAttemptCount == null ? 0 : currentAttemptCount) + 1;
    jdbcTemplate.update(
        "UPDATE agent_runs SET status = ?, updated_at = ? WHERE id = ?",
        AgentRunStatus.RUNNING.name(),
        Timestamp.from(now),
        runId
    );
    jdbcTemplate.update(
        """
            UPDATE agent_jobs
            SET status = ?, locked_by = ?, locked_until = ?, attempt_count = ?, updated_at = ?
            WHERE id = ?
            """,
        AgentJobStatus.RUNNING.name(),
        workerKind,
        Timestamp.from(now.plusSeconds(60)),
        nextAttemptCount,
        Timestamp.from(now),
        jobId
    );
    jdbcTemplate.update(
        """
            INSERT INTO run_attempts (id, run_id, job_id, worker_kind, started_at, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
        attemptId(runId, jobId, nextAttemptCount),
        runId,
        jobId,
        workerKind,
        Timestamp.from(now),
        AgentJobStatus.RUNNING.name()
    );
  }

  @Override
  public void cancel(String runId, Instant now) {
    int updatedRuns = jdbcTemplate.update(
        """
            UPDATE agent_runs
            SET status = ?, updated_at = ?
            WHERE id = ?
              AND status IN (?, ?, ?)
            """,
        AgentRunStatus.CANCELED.name(),
        Timestamp.from(now),
        runId,
        AgentRunStatus.CREATED.name(),
        AgentRunStatus.QUEUED.name(),
        AgentRunStatus.RUNNING.name()
    );
    if (updatedRuns == 0) {
      return;
    }
    jdbcTemplate.update(
        """
            UPDATE agent_jobs
            SET status = ?, locked_until = NULL, updated_at = ?
            WHERE run_id = ?
              AND status IN (?, ?)
            """,
        AgentJobStatus.CANCELED.name(),
        Timestamp.from(now),
        runId,
        AgentJobStatus.QUEUED.name(),
        AgentJobStatus.RUNNING.name()
    );
    jdbcTemplate.update(
        """
            UPDATE run_attempts
            SET finished_at = ?, status = ?, error_code = NULL
            WHERE run_id = ?
              AND finished_at IS NULL
            """,
        Timestamp.from(now),
        AgentJobStatus.CANCELED.name(),
        runId
    );
  }

  @Override
  public void retry(String runId, String jobId, String errorCode, Instant now) {
    int updatedRuns = jdbcTemplate.update(
        """
            UPDATE agent_runs
            SET status = ?, error_code = NULL, updated_at = ?
            WHERE id = ?
              AND status = ?
            """,
        AgentRunStatus.QUEUED.name(),
        Timestamp.from(now),
        runId,
        AgentRunStatus.RUNNING.name()
    );
    if (updatedRuns == 0) {
      return;
    }
    jdbcTemplate.update(
        """
            UPDATE agent_jobs
            SET status = ?, available_at = ?, locked_by = NULL, locked_until = NULL, updated_at = ?
            WHERE id = ?
              AND run_id = ?
              AND status = ?
            """,
        AgentJobStatus.QUEUED.name(),
        Timestamp.from(now),
        Timestamp.from(now),
        jobId,
        runId,
        AgentJobStatus.RUNNING.name()
    );
    finishAttempt(runId, jobId, AgentJobStatus.FAILED, errorCode, now);
  }

  @Override
  public int failStaleRunningJobs(Instant staleBefore, String errorCode, Instant now) {
    List<AgentJobRecord> staleJobs = jdbcTemplate.query(
        """
            SELECT id, run_id, status, priority, available_at, locked_by, locked_until,
              attempt_count, max_attempts, created_at, updated_at
            FROM agent_jobs
            WHERE status = ?
              AND locked_until IS NOT NULL
              AND locked_until <= ?
            ORDER BY locked_until ASC, id ASC
            """,
        jobRowMapper,
        AgentJobStatus.RUNNING.name(),
        Timestamp.from(staleBefore)
    );
    int recovered = 0;
    for (AgentJobRecord job : staleJobs) {
      int updatedRuns = jdbcTemplate.update(
          """
              UPDATE agent_runs
              SET status = ?, error_code = ?, updated_at = ?
              WHERE id = ?
                AND status = ?
              """,
          AgentRunStatus.FAILED.name(),
          errorCode,
          Timestamp.from(now),
          job.runId(),
          AgentRunStatus.RUNNING.name()
      );
      if (updatedRuns == 0) {
        continue;
      }
      jdbcTemplate.update(
          """
              UPDATE agent_jobs
              SET status = ?, locked_until = NULL, updated_at = ?
              WHERE id = ?
                AND run_id = ?
                AND status = ?
              """,
          AgentJobStatus.FAILED.name(),
          Timestamp.from(now),
          job.jobId(),
          job.runId(),
          AgentJobStatus.RUNNING.name()
      );
      finishAttempt(job.runId(), job.jobId(), AgentJobStatus.FAILED, errorCode, now);
      recovered++;
    }
    return recovered;
  }

  @Override
  public void complete(
      String runId,
      String jobId,
      String workerKind,
      AgentWorkerResponse response,
      AgentRunStatus runStatus,
      Instant now
  ) {
    if (isTerminalRun(runId)) {
      return;
    }
    jdbcTemplate.update(
        """
            UPDATE agent_runs
            SET status = ?, output_kind = ?, display_text = ?, requires_approval = ?,
              requires_confirmation = ?, wrote_workspace = ?, artifact_refs = ?,
              target_workspace_paths = ?, error_code = NULL, updated_at = ?
            WHERE id = ?
            """,
        runStatus.name(),
        response.outputKind(),
        response.displayText(),
        response.requiresApproval(),
        response.requiresConfirmation(),
        response.wroteWorkspace(),
        writeStringList(safeList(response.artifactRefs())),
        writeStringList(safeList(response.targetWorkspacePaths())),
        Timestamp.from(now),
        runId
    );
    jdbcTemplate.update(
        """
            UPDATE agent_jobs
            SET status = ?, locked_until = NULL, updated_at = ?
            WHERE id = ?
            """,
        AgentJobStatus.SUCCEEDED.name(),
        Timestamp.from(now),
        jobId
    );
    finishAttempt(runId, jobId, AgentJobStatus.SUCCEEDED, null, now);
  }

  @Override
  public void fail(String runId, String jobId, String workerKind, String errorCode, Instant now) {
    if (isTerminalRun(runId)) {
      return;
    }
    jdbcTemplate.update(
        "UPDATE agent_runs SET status = ?, error_code = ?, updated_at = ? WHERE id = ?",
        AgentRunStatus.FAILED.name(),
        errorCode,
        Timestamp.from(now),
        runId
    );
    jdbcTemplate.update(
        "UPDATE agent_jobs SET status = ?, locked_until = NULL, updated_at = ? WHERE id = ?",
        AgentJobStatus.FAILED.name(),
        Timestamp.from(now),
        jobId
    );
    finishAttempt(runId, jobId, AgentJobStatus.FAILED, errorCode, now);
  }

  private void finishAttempt(
      String runId,
      String jobId,
      AgentJobStatus status,
      String errorCode,
      Instant now
  ) {
    jdbcTemplate.update(
        """
            UPDATE run_attempts
            SET finished_at = ?, status = ?, error_code = ?
            WHERE run_id = ?
              AND job_id = ?
              AND finished_at IS NULL
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """,
        Timestamp.from(now),
        status.name(),
        errorCode,
        runId,
        jobId
    );
  }

  private boolean isTerminalRun(String runId) {
    try {
      String status = jdbcTemplate.queryForObject(
          "SELECT status FROM agent_runs WHERE id = ?",
          String.class,
          runId
      );
      return status != null && isTerminal(AgentRunStatus.valueOf(status));
    } catch (EmptyResultDataAccessException exception) {
      return false;
    }
  }

  private static boolean isTerminal(AgentRunStatus status) {
    return status == AgentRunStatus.SUCCEEDED
        || status == AgentRunStatus.SUCCEEDED_WITH_WARNINGS
        || status == AgentRunStatus.FAILED
        || status == AgentRunStatus.CANCELED;
  }

  private AgentRunRecord mapRun(ResultSet resultSet, int rowNum) throws SQLException {
    return new AgentRunRecord(
        resultSet.getString("id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("requested_by_user_id"),
        resultSet.getString("user_message"),
        resultSet.getString("mode"),
        resultSet.getBoolean("execute_requested"),
        resultSet.getBoolean("auto_approve"),
        AgentRunStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("output_kind"),
        resultSet.getString("display_text"),
        resultSet.getBoolean("requires_approval"),
        resultSet.getBoolean("requires_confirmation"),
        resultSet.getBoolean("wrote_workspace"),
        readStringList(resultSet.getString("artifact_refs")),
        readStringList(resultSet.getString("target_workspace_paths")),
        resultSet.getString("error_code"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant()
    );
  }

  private AgentJobRecord mapJob(ResultSet resultSet, int rowNum) throws SQLException {
    return new AgentJobRecord(
        resultSet.getString("id"),
        resultSet.getString("run_id"),
        AgentJobStatus.valueOf(resultSet.getString("status")),
        resultSet.getInt("priority"),
        resultSet.getTimestamp("available_at").toInstant(),
        resultSet.getString("locked_by"),
        toInstant(resultSet.getTimestamp("locked_until")),
        resultSet.getInt("attempt_count"),
        resultSet.getInt("max_attempts"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant()
    );
  }

  private RunAttemptRecord mapAttempt(ResultSet resultSet, int rowNum) throws SQLException {
    return new RunAttemptRecord(
        resultSet.getString("id"),
        resultSet.getString("run_id"),
        resultSet.getString("job_id"),
        resultSet.getString("worker_kind"),
        resultSet.getTimestamp("started_at").toInstant(),
        toInstant(resultSet.getTimestamp("finished_at")),
        AgentJobStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("error_code")
    );
  }

  private String writeStringList(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new AgentWorkerException("Unable to serialize run list field", exception);
    }
  }

  private List<String> readStringList(String json) {
    try {
      return objectMapper.readValue(json, STRING_LIST);
    } catch (JsonProcessingException exception) {
      throw new AgentWorkerException("Unable to parse run list field", exception);
    }
  }

  private static List<String> safeList(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static String attemptId(String runId, String jobId, int attemptCount) {
    return "attempt_%s_%s_%d".formatted(runId, jobId, attemptCount);
  }
}
