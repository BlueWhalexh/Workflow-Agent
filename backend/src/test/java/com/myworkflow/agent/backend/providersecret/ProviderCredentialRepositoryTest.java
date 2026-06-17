package com.myworkflow.agent.backend.providersecret;

import static org.assertj.core.api.Assertions.assertThat;

import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository.ProviderCredentialMetadata;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProviderCredentialRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_provider_credential_repository_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void savesAndResolvesActiveMetadataByTeamAndWorkspaceScopeWithoutRawSecretFields() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      seedTeamAndWorkspaces(jdbcTemplate);

      ProviderCredentialRepository repository = new ProviderCredentialRepository(jdbcTemplate);
      repository.save(new ProviderCredentialMetadata(
          "credential-team",
          "team-mimo",
          "team-a",
          null,
          "mimo-real",
          "mimo-v2.5",
          "https://token-plan-cn.xiaomimimo.com/v1",
          "secret://team-a/provider/mimo",
          "ACTIVE"
      ));
      repository.save(new ProviderCredentialMetadata(
          "credential-workspace",
          "workspace-mimo",
          "team-a",
          "workspace-a",
          "mimo-real",
          "mimo-v2.5",
          "https://token-plan-cn.xiaomimimo.com/v1",
          "secret://team-a/workspace-a/provider/mimo",
          "ACTIVE"
      ));
      repository.save(new ProviderCredentialMetadata(
          "credential-disabled",
          "disabled-mimo",
          "team-a",
          null,
          "mimo-real",
          "mimo-v2.5",
          "https://token-plan-cn.xiaomimimo.com/v1",
          "secret://team-a/provider/disabled-mimo",
          "DISABLED"
      ));

      assertThat(repository.findActiveByScope("team-a", "workspace-a", "team-mimo"))
          .hasValueSatisfying(credential -> {
            assertThat(credential.provider()).isEqualTo("mimo-real");
            assertThat(credential.apiKeySecretRef()).isEqualTo("secret://team-a/provider/mimo");
          });
      assertThat(repository.findActiveByScope("team-a", "workspace-a", "workspace-mimo"))
          .hasValueSatisfying(credential ->
              assertThat(credential.workspaceId()).isEqualTo("workspace-a"));
      assertThat(repository.findActiveByScope("team-a", "workspace-b", "workspace-mimo")).isEmpty();
      assertThat(repository.findActiveByScope("team-b", "workspace-c", "team-mimo")).isEmpty();
      assertThat(repository.findActiveByScope("team-a", "workspace-a", "disabled-mimo")).isEmpty();
      assertThat(repository.listByWorkspaceScope("team-a", "workspace-a"))
          .extracting(ProviderCredentialMetadata::credentialRef)
          .containsExactly("workspace-mimo");

      assertThat(repository.disableWorkspaceCredential("team-a", "workspace-a", "workspace-mimo"))
          .hasValueSatisfying(credential -> {
            assertThat(credential.credentialRef()).isEqualTo("workspace-mimo");
            assertThat(credential.workspaceId()).isEqualTo("workspace-a");
            assertThat(credential.status()).isEqualTo("DISABLED");
            assertThat(credential.apiKeySecretRef()).isEqualTo("secret://team-a/workspace-a/provider/mimo");
          });
      assertThat(repository.findActiveByScope("team-a", "workspace-a", "workspace-mimo")).isEmpty();
      assertThat(repository.listByWorkspaceScope("team-a", "workspace-a"))
          .extracting(ProviderCredentialMetadata::credentialRef, ProviderCredentialMetadata::status)
          .containsExactly(org.assertj.core.api.Assertions.tuple("workspace-mimo", "DISABLED"));
      assertThat(repository.disableWorkspaceCredential("team-a", "workspace-b", "workspace-mimo")).isEmpty();
      assertThat(repository.disableWorkspaceCredential("team-b", "workspace-c", "workspace-mimo")).isEmpty();

      assertThat(repository.listByWorkspaceScope("team-a", "workspace-b")).isEmpty();
      assertThat(repository.listByWorkspaceScope("team-b", "workspace-c")).isEmpty();

      assertThat(Arrays.stream(ProviderCredentialMetadata.class.getRecordComponents())
          .map(component -> component.getName()))
          .contains("apiKeySecretRef")
          .doesNotContain("apiKey", "token", "authorization", "secretValue", "rawSecret");
    }
  }

  private static void seedTeamAndWorkspaces(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update(
        "INSERT INTO teams (id, name, status) VALUES (?, ?, 'ACTIVE')",
        "team-a",
        "Team A"
    );
    jdbcTemplate.update(
        "INSERT INTO teams (id, name, status) VALUES (?, ?, 'ACTIVE')",
        "team-b",
        "Team B"
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
    jdbcTemplate.update(
        """
            INSERT INTO workspaces (id, team_id, name, storage_mode, server_storage_ref, default_branch, status)
            VALUES (?, ?, ?, 'SERVER_MANAGED', ?, 'main', 'ACTIVE')
            """,
        "workspace-c",
        "team-b",
        "Workspace C",
        "/tmp/workspace-c"
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
