package com.myworkflow.agent.backend.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.run.AgentJobRecord;
import com.myworkflow.agent.backend.run.AgentRunRecord;
import com.myworkflow.agent.backend.run.AgentRunRepository;
import com.myworkflow.agent.backend.run.JdbcAgentRunRepository;
import com.myworkflow.agent.backend.workspace.JdbcWorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcArtifactRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_agent_artifact_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void persistsArtifactRefsAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_artifact_jdbc",
          "team_artifact_jdbc",
          "Artifact JDBC",
          "teams/team_artifact_jdbc/workspaces/ws_artifact_jdbc/content",
          "main",
          WorkspaceStatus.ACTIVE,
          Instant.parse("2026-06-14T00:00:00Z")
      );
      workspaceRepository.save(workspace, "user_artifact_jdbc", WorkspaceRole.WORKSPACE_OWNER);

      AgentRunRepository runRepository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      Instant now = Instant.parse("2026-06-14T02:00:00Z");
      runRepository.create(
          AgentRunRecord.queued(
              "run_artifact_jdbc",
              "ws_artifact_jdbc",
              "user_artifact_jdbc",
              "artifact",
              "deterministic-open-agent",
              false,
              false,
              now
          ),
          AgentJobRecord.queued("job_artifact_jdbc", "run_artifact_jdbc", now, 3)
      );

      ArtifactRepository artifactRepository = new JdbcArtifactRepository(jdbcTemplate);
      artifactRepository.registerRunArtifacts(
          "run_artifact_jdbc",
          "ws_artifact_jdbc",
          List.of(
              ".agent-runs/run_artifact_jdbc/artifact.json",
              ".agent-runs/run_artifact_jdbc/raw-provider/response.json"
          ),
          now
      );

      List<ArtifactRefRecord> artifacts = artifactRepository.findByRunId("run_artifact_jdbc");
      assertThat(artifacts).hasSize(2);
      assertThat(artifacts)
          .extracting(ArtifactRefRecord::artifactRef)
          .containsExactly(
              ".agent-runs/run_artifact_jdbc/artifact.json",
              ".agent-runs/run_artifact_jdbc/raw-provider/response.json"
          );
      assertThat(artifacts.get(1).kind()).isEqualTo("RAW_PROVIDER");
      assertThat(artifacts.get(1).redactionStatus()).isEqualTo("REDACTED");
      assertThat(artifactRepository.findById(artifacts.get(0).artifactId())).contains(artifacts.get(0));

      Integer migrationVersion = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ?",
          Integer.class,
          "4"
      );
      assertThat(migrationVersion).isEqualTo(1);
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
