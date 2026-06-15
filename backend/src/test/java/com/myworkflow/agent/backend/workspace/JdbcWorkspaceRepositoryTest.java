package com.myworkflow.agent.backend.workspace;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcWorkspaceRepositoryTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_agent_test")
      .withUsername("test")
      .withPassword("test");

  @Test
  void jdbcRepositorySatisfiesWorkspaceRepositoryContractAgainstMySql() {
    try (HikariDataSource dataSource = dataSource()) {
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      WorkspaceRepositoryContract.assertRepositoryContract(
          new JdbcWorkspaceRepository(new JdbcTemplate(dataSource))
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
}
