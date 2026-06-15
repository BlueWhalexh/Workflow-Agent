package com.myworkflow.agent.backend.artifact;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcArtifactRepository implements ArtifactRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcArtifactRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void registerRunArtifacts(
      String runId,
      String workspaceId,
      List<String> artifactRefs,
      Instant now
  ) {
    for (ArtifactRefRecord artifact : ArtifactRefFactory.create(runId, workspaceId, artifactRefs, now)) {
      jdbcTemplate.update(
          """
              INSERT INTO artifact_refs (
                id, run_id, workspace_id, artifact_ref, kind, redaction_status,
                content_type, sort_order, created_at
              )
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
              """,
          artifact.artifactId(),
          artifact.runId(),
          artifact.workspaceId(),
          artifact.artifactRef(),
          artifact.kind(),
          artifact.redactionStatus(),
          artifact.contentType(),
          artifact.sortOrder(),
          Timestamp.from(artifact.createdAt())
      );
    }
  }

  @Override
  public List<ArtifactRefRecord> findByRunId(String runId) {
    return jdbcTemplate.query(
        """
            SELECT id, run_id, workspace_id, artifact_ref, kind, redaction_status,
              content_type, sort_order, created_at
            FROM artifact_refs
            WHERE run_id = ?
            ORDER BY sort_order ASC, id ASC
            """,
        this::mapArtifact,
        runId
    );
  }

  @Override
  public Optional<ArtifactRefRecord> findById(String artifactId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id, run_id, workspace_id, artifact_ref, kind, redaction_status,
                content_type, sort_order, created_at
              FROM artifact_refs
              WHERE id = ?
              """,
          this::mapArtifact,
          artifactId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  private ArtifactRefRecord mapArtifact(ResultSet resultSet, int rowNum) throws SQLException {
    return new ArtifactRefRecord(
        resultSet.getString("id"),
        resultSet.getString("run_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("artifact_ref"),
        resultSet.getString("kind"),
        resultSet.getString("redaction_status"),
        resultSet.getString("content_type"),
        resultSet.getInt("sort_order"),
        resultSet.getTimestamp("created_at").toInstant()
    );
  }
}
