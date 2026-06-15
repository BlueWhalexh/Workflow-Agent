package com.myworkflow.agent.backend.providersecret;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProviderCredentialSchemaTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_provider_credential_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void migratesProviderCredentialMetadataSchemaWithoutRawSecretColumnsAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

      assertThat(tableNames(jdbcTemplate)).contains("provider_credentials");
      assertThat(columnNames(jdbcTemplate, "provider_credentials"))
          .contains(
              "id",
              "credential_ref",
              "team_id",
              "workspace_id",
              "provider",
              "model",
              "base_url",
              "api_key_secret_ref",
              "status",
              "created_at",
              "updated_at"
          )
          .doesNotContain(
              "api_key",
              "token",
              "authorization",
              "secret_value",
              "raw_secret"
          );
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

  private static List<String> tableNames(JdbcTemplate jdbcTemplate) {
    return jdbcTemplate.queryForList(
        """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
            """,
        String.class
    );
  }

  private static List<String> columnNames(JdbcTemplate jdbcTemplate, String tableName) {
    return jdbcTemplate.queryForList(
        """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
            ORDER BY ordinal_position
            """,
        String.class,
        tableName
    );
  }
}
