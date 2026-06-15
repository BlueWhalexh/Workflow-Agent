package com.myworkflow.agent.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.myworkflow.agent.backend.run.AgentRunRecord;
import com.myworkflow.agent.backend.run.AgentJobRecord;
import com.myworkflow.agent.backend.run.JdbcAgentRunRepository;
import com.myworkflow.agent.backend.workspace.JdbcWorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;

@Testcontainers
class JdbcAuditRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_audit_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void appendsAuditEventsByRunWithoutPayloadLeakage() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      JdbcWorkspaceRepository workspaceRepository = new JdbcWorkspaceRepository(jdbcTemplate);
      JdbcAgentRunRepository runRepository = new JdbcAgentRunRepository(jdbcTemplate, new ObjectMapper());
      JdbcAuditRepository auditRepository = new JdbcAuditRepository(jdbcTemplate);

      seedContractReferences(workspaceRepository, runRepository);
      AuditRepositoryContract.assertWorkspaceAuditQueryContract(auditRepository);

      Instant now = Instant.parse("2026-06-14T09:00:00Z");
      WorkspaceRecord workspace = new WorkspaceRecord(
          "ws_audit",
          "team_audit",
          "Audit Workspace",
          "teams/team_audit/workspaces/ws_audit/content",
          "main",
          WorkspaceStatus.ACTIVE,
          now
      );
      workspaceRepository.save(workspace, "owner_audit", WorkspaceRole.WORKSPACE_OWNER);
      workspaceRepository.grantAccess(workspace.workspaceId(), "viewer_audit", workspace.teamId(), WorkspaceRole.WORKSPACE_VIEWER);
      AgentRunRecord run = AgentRunRecord.queued(
          "run_audit",
          workspace.workspaceId(),
          "owner_audit",
          "audit",
          "deterministic-open-agent",
          false,
          false,
          now
      );
      runRepository.create(run, AgentJobRecord.queued("job_audit", run.runId(), now, 3));

      auditRepository.append(
          "owner_audit",
          workspace.teamId(),
          workspace.workspaceId(),
          run.runId(),
          "AGENT_RUN_REQUESTED",
          "Run requested",
          now.plusSeconds(1)
      );
      auditRepository.append(
          "viewer_audit",
          workspace.teamId(),
          workspace.workspaceId(),
          run.runId(),
          "ARTIFACT_READ",
          "Artifact read",
          now.plusSeconds(2)
      );
      auditRepository.append(
          "owner_audit",
          workspace.teamId(),
          workspace.workspaceId(),
          null,
          "WORKSPACE_MEMBER_GRANTED",
          "Workspace member granted",
          now.plusSeconds(3)
      );

      assertThat(auditRepository.findByRunId(run.runId()))
          .extracting(AuditEventRecord::eventType)
          .containsExactly("AGENT_RUN_REQUESTED", "ARTIFACT_READ");
      assertThat(auditRepository.findByRunId(run.runId()))
          .extracting(AuditEventRecord::actorUserId)
          .containsExactly("owner_audit", "viewer_audit");
      assertThat(auditRepository.findByRunId(run.runId()))
          .allSatisfy((event) -> {
            assertThat(event.workspaceId()).isEqualTo(workspace.workspaceId());
            assertThat(event.teamId()).isEqualTo(workspace.teamId());
            assertThat(event.message()).doesNotContain("token");
          });
      assertThat(auditRepository.findByWorkspaceId(workspace.workspaceId()))
          .extracting(AuditEventRecord::eventType)
          .containsExactly("AGENT_RUN_REQUESTED", "ARTIFACT_READ", "WORKSPACE_MEMBER_GRANTED");
      assertThat(auditRepository.findByWorkspaceId(workspace.workspaceId()))
          .allSatisfy((event) -> assertThat(event.message()).doesNotContain("token"));
    }
  }

  private static HikariDataSource dataSource() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(MYSQL.getJdbcUrl());
    dataSource.setUsername(MYSQL.getUsername());
    dataSource.setPassword(MYSQL.getPassword());
    return dataSource;
  }

  private static void seedContractReferences(
      JdbcWorkspaceRepository workspaceRepository,
      JdbcAgentRunRepository runRepository
  ) {
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    WorkspaceRecord workspace = new WorkspaceRecord(
        "ws_audit_contract",
        "team_audit_contract",
        "Audit Contract Workspace",
        "teams/team_audit_contract/workspaces/ws_audit_contract/content",
        "main",
        WorkspaceStatus.ACTIVE,
        now
    );
    WorkspaceRecord otherWorkspace = new WorkspaceRecord(
        "ws_other_audit_contract",
        "team_audit_contract",
        "Other Audit Contract Workspace",
        "teams/team_audit_contract/workspaces/ws_other_audit_contract/content",
        "main",
        WorkspaceStatus.ACTIVE,
        now
    );
    workspaceRepository.save(workspace, "owner_audit_contract", WorkspaceRole.WORKSPACE_OWNER);
    workspaceRepository.save(otherWorkspace, "owner_audit_contract", WorkspaceRole.WORKSPACE_OWNER);
    runRepository.create(
        AgentRunRecord.queued(
            "run_contract_1",
            workspace.workspaceId(),
            "owner_audit_contract",
            "audit contract 1",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_contract_1", "run_contract_1", now, 3)
    );
    runRepository.create(
        AgentRunRecord.queued(
            "run_contract_2",
            workspace.workspaceId(),
            "owner_audit_contract",
            "audit contract 2",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_contract_2", "run_contract_2", now, 3)
    );
    runRepository.create(
        AgentRunRecord.queued(
            "run_contract_3",
            otherWorkspace.workspaceId(),
            "owner_audit_contract",
            "audit contract 3",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_contract_3", "run_contract_3", now, 3)
    );
  }
}
