package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.workspace.JdbcWorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcAgentRunRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_agent_run_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void persistsRunJobAndAttemptStateAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_run_jdbc",
          "team_run_jdbc",
          "Run JDBC",
          "teams/team_run_jdbc/workspaces/ws_run_jdbc/content",
          "main",
          WorkspaceStatus.ACTIVE,
          Instant.parse("2026-06-14T00:00:00Z")
      );
      workspaceRepository.save(workspace, "user_run_jdbc", WorkspaceRole.WORKSPACE_OWNER);

      AgentRunRepository repository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      Instant createdAt = Instant.parse("2026-06-14T01:00:00Z");
      AgentRunRecord run = AgentRunRecord.queued(
          "run_jdbc",
          "ws_run_jdbc",
          "user_run_jdbc",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          createdAt
      );
      AgentJobRecord job = AgentJobRecord.queued("job_jdbc", "run_jdbc", createdAt, 3);

      repository.create(run, job);
      repository.markRunning("run_jdbc", "job_jdbc", "LOCAL_TS_WORKER", createdAt.plusSeconds(1));
      repository.complete(
          "run_jdbc",
          "job_jdbc",
          "LOCAL_TS_WORKER",
          new AgentWorkerResponse(
              "agent-backend-response.v1",
              "run_jdbc",
              "SUCCEEDED",
              "answer",
              "Persisted answer.",
              false,
              false,
              List.of(".agent-runs/run_jdbc/artifact.json"),
              false,
              List.of()
          ),
          AgentRunStatus.SUCCEEDED,
          createdAt.plusSeconds(2)
      );

      AgentRunRecord loaded = repository.findRun("run_jdbc").orElseThrow();
      assertThat(loaded.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
      assertThat(loaded.outputKind()).isEqualTo("answer");
      assertThat(loaded.displayText()).isEqualTo("Persisted answer.");
      assertThat(loaded.artifactRefs()).containsExactly(".agent-runs/run_jdbc/artifact.json");
      assertThat(loaded.wroteWorkspace()).isFalse();

      assertThat(repository.findJob("job_jdbc").orElseThrow().status())
          .isEqualTo(AgentJobStatus.SUCCEEDED);
      assertThat(repository.findAttempts("run_jdbc"))
          .extracting(RunAttemptRecord::workerKind)
          .containsExactly("LOCAL_TS_WORKER");

      Integer migrationVersion = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ?",
          Integer.class,
          "3"
      );
      assertThat(migrationVersion).isEqualTo(1);
      Timestamp finishedAt = jdbcTemplate.queryForObject(
          "SELECT finished_at FROM run_attempts WHERE run_id = ?",
          Timestamp.class,
          "run_jdbc"
      );
      assertThat(finishedAt.toInstant()).isEqualTo(createdAt.plusSeconds(2));
    }
  }

  @Test
  void completeDoesNotOverwriteCanceledRunAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .cleanDisabled(false)
          .load()
          .clean();
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_run_cancel_jdbc",
          "team_run_cancel_jdbc",
          "Run Cancel JDBC",
          "teams/team_run_cancel_jdbc/workspaces/ws_run_cancel_jdbc/content",
          "main",
          WorkspaceStatus.ACTIVE,
          Instant.parse("2026-06-14T00:00:00Z")
      );
      workspaceRepository.save(workspace, "user_run_cancel_jdbc", WorkspaceRole.WORKSPACE_OWNER);

      AgentRunRepository repository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      Instant createdAt = Instant.parse("2026-06-14T02:00:00Z");
      repository.create(
          AgentRunRecord.queued(
              "run_cancel_jdbc",
              "ws_run_cancel_jdbc",
              "user_run_cancel_jdbc",
              "cancel",
              "deterministic-open-agent",
              false,
              false,
              createdAt
          ),
          AgentJobRecord.queued("job_cancel_jdbc", "run_cancel_jdbc", createdAt, 3)
      );
      repository.markRunning(
          "run_cancel_jdbc",
          "job_cancel_jdbc",
          "LOCAL_TS_WORKER",
          createdAt.plusSeconds(1)
      );
      repository.cancel("run_cancel_jdbc", createdAt.plusSeconds(2));
      repository.complete(
          "run_cancel_jdbc",
          "job_cancel_jdbc",
          "LOCAL_TS_WORKER",
          new AgentWorkerResponse(
              "agent-backend-response.v1",
              "run_cancel_jdbc",
              "SUCCEEDED",
              "answer",
              "late success",
              false,
              false,
              List.of(".agent-runs/run_cancel_jdbc/artifact.json"),
              false,
              List.of()
          ),
          AgentRunStatus.SUCCEEDED,
          createdAt.plusSeconds(3)
      );

      assertThat(repository.findRun("run_cancel_jdbc").orElseThrow().status())
          .isEqualTo(AgentRunStatus.CANCELED);
      assertThat(repository.findJob("job_cancel_jdbc").orElseThrow().status())
          .isEqualTo(AgentJobStatus.CANCELED);
      assertThat(repository.findAttempts("run_cancel_jdbc"))
          .extracting(RunAttemptRecord::status)
          .containsExactly(AgentJobStatus.CANCELED);
    }
  }

  @Test
  void retryClosesAttemptAndRequeuesJobAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .cleanDisabled(false)
          .load()
          .clean();
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_run_retry_jdbc",
          "team_run_retry_jdbc",
          "Run Retry JDBC",
          "teams/team_run_retry_jdbc/workspaces/ws_run_retry_jdbc/content",
          "main",
          WorkspaceStatus.ACTIVE,
          Instant.parse("2026-06-14T00:00:00Z")
      );
      workspaceRepository.save(workspace, "user_run_retry_jdbc", WorkspaceRole.WORKSPACE_OWNER);

      AgentRunRepository repository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      Instant createdAt = Instant.parse("2026-06-14T03:00:00Z");
      repository.create(
          AgentRunRecord.queued(
              "run_retry_jdbc",
              "ws_run_retry_jdbc",
              "user_run_retry_jdbc",
              "retry",
              "deterministic-open-agent",
              false,
              false,
              createdAt
          ),
          AgentJobRecord.queued("job_retry_jdbc", "run_retry_jdbc", createdAt, 3)
      );
      repository.markRunning(
          "run_retry_jdbc",
          "job_retry_jdbc",
          "LOCAL_TS_WORKER",
          createdAt.plusSeconds(1)
      );
      repository.retry("run_retry_jdbc", "job_retry_jdbc", "WORKER_FAILED", createdAt.plusSeconds(2));

      assertThat(repository.findRun("run_retry_jdbc").orElseThrow().status())
          .isEqualTo(AgentRunStatus.QUEUED);
      AgentJobRecord job = repository.findJob("job_retry_jdbc").orElseThrow();
      assertThat(job.status()).isEqualTo(AgentJobStatus.QUEUED);
      assertThat(job.lockedUntil()).isNull();
      assertThat(repository.findAttempts("run_retry_jdbc"))
          .extracting(RunAttemptRecord::status)
          .containsExactly(AgentJobStatus.FAILED);
      assertThat(repository.findAttempts("run_retry_jdbc"))
          .extracting(RunAttemptRecord::errorCode)
          .containsExactly("WORKER_FAILED");
    }
  }

  @Test
  void failStaleRunningJobsAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .cleanDisabled(false)
          .load()
          .clean();
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_run_stale_jdbc",
          "team_run_stale_jdbc",
          "Run Stale JDBC",
          "teams/team_run_stale_jdbc/workspaces/ws_run_stale_jdbc/content",
          "main",
          WorkspaceStatus.ACTIVE,
          Instant.parse("2026-06-14T00:00:00Z")
      );
      workspaceRepository.save(workspace, "user_run_stale_jdbc", WorkspaceRole.WORKSPACE_OWNER);

      AgentRunRepository repository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      Instant createdAt = Instant.parse("2026-06-14T04:00:00Z");
      repository.create(
          AgentRunRecord.queued(
              "run_stale_jdbc",
              "ws_run_stale_jdbc",
              "user_run_stale_jdbc",
              "stale",
              "deterministic-open-agent",
              false,
              false,
              createdAt
          ),
          AgentJobRecord.queued("job_stale_jdbc", "run_stale_jdbc", createdAt, 3)
      );
      repository.markRunning(
          "run_stale_jdbc",
          "job_stale_jdbc",
          "LOCAL_TS_WORKER",
          createdAt.plusSeconds(1)
      );

      int recovered = repository.failStaleRunningJobs(
          createdAt.plusSeconds(62),
          "STALE_LOCK",
          createdAt.plusSeconds(63)
      );

      assertThat(recovered).isEqualTo(1);
      AgentRunRecord run = repository.findRun("run_stale_jdbc").orElseThrow();
      assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
      assertThat(run.errorCode()).isEqualTo("STALE_LOCK");
      AgentJobRecord job = repository.findJob("job_stale_jdbc").orElseThrow();
      assertThat(job.status()).isEqualTo(AgentJobStatus.FAILED);
      assertThat(job.lockedUntil()).isNull();
      RunAttemptRecord attempt = repository.findAttempts("run_stale_jdbc").get(0);
      assertThat(attempt.status()).isEqualTo(AgentJobStatus.FAILED);
      assertThat(attempt.errorCode()).isEqualTo("STALE_LOCK");
      assertThat(attempt.finishedAt()).isEqualTo(createdAt.plusSeconds(63));
    }
  }

  private static HikariDataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(MYSQL.getJdbcUrl());
    config.setUsername(MYSQL.getUsername());
    config.setPassword(MYSQL.getPassword());
    config.setDriverClassName(MYSQL.getDriverClassName());
    return new HikariDataSource(config);
  }
}
