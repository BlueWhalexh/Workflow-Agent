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
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcRunEventRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_run_event_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void persistsAndListsRunEventsInOrderAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      migrateClean(dataSource);

      JdbcRunEventRepository repository = new JdbcRunEventRepository(new JdbcTemplate(dataSource));
      String runId = "run_event_jdbc_order";
      Instant first = Instant.parse("2026-06-14T09:00:00Z");
      Instant second = Instant.parse("2026-06-14T09:00:01Z");
      createRun(dataSource, runId, first);
      repository.append(runId, "RUN_QUEUED", AgentRunStatus.QUEUED, "queued", first);
      repository.append(runId, "RUNNING", AgentRunStatus.RUNNING, "running", second);

      assertThat(repository.findByRunId(runId))
          .extracting(RunEventRecord::eventType)
          .containsExactly("RUN_QUEUED", "RUNNING");
      assertThat(repository.findByRunId(runId))
          .extracting(RunEventRecord::status)
          .containsExactly(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING);
      assertThat(repository.findByRunId(runId))
          .extracting(RunEventRecord::message)
          .containsExactly("queued", "running");
    }
  }

  @Test
  void preservesAppendOrderWhenEventsShareTimestampAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      migrateClean(dataSource);

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      JdbcRunEventRepository repository = new JdbcRunEventRepository(jdbcTemplate);
      String runId = "run_event_same_timestamp";
      Instant sameTime = Instant.parse("2026-06-14T09:00:00Z");
      createRun(dataSource, runId, sameTime);
      jdbcTemplate.update(
          """
              INSERT INTO run_events (id, run_id, event_type, status, message, created_at)
              VALUES (?, ?, ?, ?, ?, ?)
              """,
          "evt_z_first",
          runId,
          "RUN_QUEUED",
          AgentRunStatus.QUEUED.name(),
          "queued",
          sameTime
      );
      jdbcTemplate.update(
          """
              INSERT INTO run_events (id, run_id, event_type, status, message, created_at)
              VALUES (?, ?, ?, ?, ?, ?)
              """,
          "evt_a_second",
          runId,
          "RUNNING",
          AgentRunStatus.RUNNING.name(),
          "running",
          sameTime
      );

      assertThat(repository.findByRunId(runId))
          .extracting(RunEventRecord::eventId)
          .containsExactly("evt_z_first", "evt_a_second");
      assertThat(repository.findByRunId(runId))
          .extracting(RunEventRecord::eventType)
          .containsExactly("RUN_QUEUED", "RUNNING");
    }
  }

  @Test
  void upgradesExistingRunEventTableToDurableSequenceAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway oldSchemaFlyway = flyway(dataSource)
          .target("7")
          .load();
      oldSchemaFlyway.clean();
      oldSchemaFlyway.migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      assertThat(countRunEventSequenceColumns(jdbcTemplate)).isZero();

      flyway(dataSource)
          .load()
          .migrate();

      assertThat(countRunEventSequenceColumns(jdbcTemplate)).isOne();

      JdbcRunEventRepository repository = new JdbcRunEventRepository(jdbcTemplate);
      String runId = "run_event_upgrade_sequence";
      Instant sameTime = Instant.parse("2026-06-14T09:00:00Z");
      createRun(dataSource, runId, sameTime);
      repository.append(runId, "RUN_QUEUED", AgentRunStatus.QUEUED, "queued", sameTime);
      repository.append(runId, "RUNNING", AgentRunStatus.RUNNING, "running", sameTime);

      assertThat(repository.findByRunId(runId))
          .extracting(RunEventRecord::eventType)
          .containsExactly("RUN_QUEUED", "RUNNING");
    }
  }

  private static void createRun(HikariDataSource dataSource, String runId, Instant now) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
    WorkspaceRecord workspace = new WorkspaceRecord(
        "ws_run_event_jdbc",
        "team_run_event_jdbc",
        "Run Event JDBC",
        "teams/team_run_event_jdbc/workspaces/ws_run_event_jdbc/content",
        "main",
        WorkspaceStatus.ACTIVE,
        now
    );
    workspaceRepository.save(workspace, "user_run_event_jdbc", WorkspaceRole.WORKSPACE_OWNER);
    AgentRunRepository runRepository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
    runRepository.create(
        AgentRunRecord.queued(
            runId,
            "ws_run_event_jdbc",
            "user_run_event_jdbc",
            "events",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_" + runId, runId, now, 3)
    );
  }

  private static HikariDataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(MYSQL.getJdbcUrl());
    config.setUsername(MYSQL.getUsername());
    config.setPassword(MYSQL.getPassword());
    config.setDriverClassName(MYSQL.getDriverClassName());
    return new HikariDataSource(config);
  }

  private static void migrateClean(HikariDataSource dataSource) {
    Flyway flyway = flyway(dataSource).load();
    flyway.clean();
    flyway.migrate();
  }

  private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(HikariDataSource dataSource) {
    return Flyway.configure()
        .cleanDisabled(false)
        .dataSource(dataSource)
        .locations("classpath:db/migration");
  }

  private static Long countRunEventSequenceColumns(JdbcTemplate jdbcTemplate) {
    return jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'run_events'
              AND column_name = 'event_sequence'
            """,
        Long.class
    );
  }
}
