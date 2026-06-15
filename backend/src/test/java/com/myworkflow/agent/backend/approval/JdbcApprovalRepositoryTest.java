package com.myworkflow.agent.backend.approval;

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
class JdbcApprovalRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_agent_approval_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void persistsApprovalRequestAndDecisionAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      WorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_approval_jdbc",
          "team_approval_jdbc",
          "Approval JDBC",
          "teams/team_approval_jdbc/workspaces/ws_approval_jdbc/content",
          "main",
          WorkspaceStatus.ACTIVE,
          Instant.parse("2026-06-14T00:00:00Z")
      );
      workspaceRepository.save(workspace, "user_approval_jdbc", WorkspaceRole.WORKSPACE_OWNER);

      AgentRunRepository runRepository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      Instant now = Instant.parse("2026-06-14T03:00:00Z");
      runRepository.create(
          AgentRunRecord.queued(
              "run_approval_jdbc",
              "ws_approval_jdbc",
              "user_approval_jdbc",
              "approval",
              "llm-open-agent",
              false,
              false,
              now
          ),
          AgentJobRecord.queued("job_approval_jdbc", "run_approval_jdbc", now, 3)
      );

      ApprovalRepository approvalRepository = new JdbcApprovalRepository(jdbcTemplate, new ObjectMapper());
      ApprovalRequestRecord created = approvalRepository.createPending(
          "run_approval_jdbc",
          "ws_approval_jdbc",
          "user_approval_jdbc",
          ".agent-runs/run_approval_jdbc/artifact.json",
          List.of("knowledge-base/drafts/run_approval_jdbc.md"),
          now
      );

      assertThat(created.status()).isEqualTo(ApprovalStatus.PENDING);
      assertThat(created.decision()).isNull();
      assertThat(approvalRepository.findByRunId("run_approval_jdbc"))
          .extracting(ApprovalRequestRecord::approvalId)
          .containsExactly(created.approvalId());

      ApprovalRequestRecord decided = approvalRepository.decide(
          created.approvalId(),
          "user_approval_jdbc",
          ApprovalDecision.REJECTED,
          now.plusSeconds(10)
      );

      assertThat(decided.status()).isEqualTo(ApprovalStatus.DECIDED);
      assertThat(decided.decision()).isEqualTo(ApprovalDecision.REJECTED);
      assertThat(decided.decidedByUserId()).isEqualTo("user_approval_jdbc");
      assertThat(decided.targetWorkspacePaths())
          .containsExactly("knowledge-base/drafts/run_approval_jdbc.md");

      Integer migrationVersion = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ?",
          Integer.class,
          "5"
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
