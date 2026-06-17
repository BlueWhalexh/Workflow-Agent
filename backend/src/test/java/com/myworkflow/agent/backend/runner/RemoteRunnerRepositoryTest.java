package com.myworkflow.agent.backend.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RemoteRunnerRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_remote_runner_repository_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void persistsWorkspaceScopedRunnerHeartbeatAndExclusiveLeaseWithoutSecretFields() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      seedTeamAndWorkspace(jdbcTemplate);

      RemoteRunnerRepository repository = new RemoteRunnerRepository(jdbcTemplate);
      Instant now = Instant.parse("2026-06-17T10:15:30Z");
      RemoteRunnerRecord registered = repository.save(new RemoteRunnerRecord(
          "runner-record-1",
          "workspace-a",
          "runner-local-1",
          "本地远程执行器",
          "http://127.0.0.1:19090/run",
          RemoteRunnerStatus.REGISTERED,
          List.of("agent-backend-response.v1", "workspace-read"),
          null,
          null,
          null,
          now,
          now
      ));

      assertThat(registered.status()).isEqualTo(RemoteRunnerStatus.REGISTERED);
      assertThat(repository.listByWorkspace("workspace-a"))
          .extracting(RemoteRunnerRecord::runnerRef, RemoteRunnerRecord::status)
          .containsExactly(tuple("runner-local-1", RemoteRunnerStatus.REGISTERED));

      RemoteRunnerRecord online = repository.recordHeartbeat("workspace-a", "runner-local-1", now.plusSeconds(5))
          .orElseThrow();
      assertThat(online.status()).isEqualTo(RemoteRunnerStatus.ONLINE);
      assertThat(online.lastSeenAt()).isEqualTo(now.plusSeconds(5));

      RemoteRunnerRecord leased = repository.claimLease(
              "workspace-a",
              "runner-local-1",
              "scheduler-local",
              now.plusSeconds(10),
              now.plusSeconds(40)
          )
          .orElseThrow();
      assertThat(leased.status()).isEqualTo(RemoteRunnerStatus.LEASED);
      assertThat(leased.leaseOwner()).isEqualTo("scheduler-local");
      assertThat(leased.leaseExpiresAt()).isEqualTo(now.plusSeconds(40));

      assertThat(repository.claimLease(
          "workspace-a",
          "runner-local-1",
          "scheduler-other",
          now.plusSeconds(20),
          now.plusSeconds(50)
      )).isEmpty();

      RemoteRunnerRecord reclaimed = repository.claimLease(
              "workspace-a",
              "runner-local-1",
              "scheduler-other",
              now.plusSeconds(41),
              now.plusSeconds(70)
          )
          .orElseThrow();
      assertThat(reclaimed.status()).isEqualTo(RemoteRunnerStatus.LEASED);
      assertThat(reclaimed.leaseOwner()).isEqualTo("scheduler-other");

      assertThat(repository.findByWorkspaceAndRunnerRef("workspace-b", "runner-local-1")).isEmpty();
      assertThat(repository.listByWorkspace("workspace-b")).isEmpty();

      assertThat(Arrays.stream(RemoteRunnerRecord.class.getRecordComponents())
          .map(component -> component.getName()))
          .contains("endpointUrl")
          .doesNotContain("runnerToken", "signatureSecret", "apiKey", "authorization", "secretValue");
    }
  }

  private static void seedTeamAndWorkspace(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update(
        "INSERT INTO teams (id, name, status) VALUES (?, ?, 'ACTIVE')",
        "team-a",
        "Team A"
    );
    jdbcTemplate.update(
        """
            INSERT INTO workspaces (id, team_id, name, storage_mode, server_storage_ref, default_branch, status)
            VALUES (?, ?, ?, 'SERVER_MANAGED', ?, 'main', 'ACTIVE')
            """,
        "workspace-a",
        "team-a",
        "Workspace A",
        "/tmp/workspace-a"
    );
    jdbcTemplate.update(
        """
            INSERT INTO workspaces (id, team_id, name, storage_mode, server_storage_ref, default_branch, status)
            VALUES (?, ?, ?, 'SERVER_MANAGED', ?, 'main', 'ACTIVE')
            """,
        "workspace-b",
        "team-a",
        "Workspace B",
        "/tmp/workspace-b"
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
}
