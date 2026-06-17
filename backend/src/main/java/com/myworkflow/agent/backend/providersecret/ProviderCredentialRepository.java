package com.myworkflow.agent.backend.providersecret;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class ProviderCredentialRepository {

  private static final RowMapper<ProviderCredentialMetadata> CREDENTIAL_ROW_MAPPER =
      ProviderCredentialRepository::mapCredential;

  private final JdbcTemplate jdbcTemplate;

  public ProviderCredentialRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public ProviderCredentialMetadata save(ProviderCredentialMetadata credential) {
    jdbcTemplate.update(
        """
            INSERT INTO provider_credentials (
              id,
              credential_ref,
              team_id,
              workspace_id,
              provider,
              model,
              base_url,
              api_key_secret_ref,
              status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              workspace_id = VALUES(workspace_id),
              provider = VALUES(provider),
              model = VALUES(model),
              base_url = VALUES(base_url),
              api_key_secret_ref = VALUES(api_key_secret_ref),
              status = VALUES(status)
            """,
        credential.id(),
        credential.credentialRef(),
        credential.teamId(),
        credential.workspaceId(),
        credential.provider(),
        credential.model(),
        credential.baseUrl(),
        credential.apiKeySecretRef(),
        credential.status()
    );
    return credential;
  }

  public Optional<ProviderCredentialMetadata> findActiveByScope(
      String teamId,
      String workspaceId,
      String credentialRef
  ) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id,
                     credential_ref,
                     team_id,
                     workspace_id,
                     provider,
                     model,
                     base_url,
                     api_key_secret_ref,
                     status
              FROM provider_credentials
              WHERE team_id = ?
                AND credential_ref = ?
                AND status = 'ACTIVE'
                AND (workspace_id IS NULL OR workspace_id = ?)
              ORDER BY CASE WHEN workspace_id = ? THEN 0 ELSE 1 END
              LIMIT 1
              """,
          CREDENTIAL_ROW_MAPPER,
          teamId,
          credentialRef,
          workspaceId,
          workspaceId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  public List<ProviderCredentialMetadata> listByWorkspaceScope(String teamId, String workspaceId) {
    return jdbcTemplate.query(
        """
            SELECT id,
                   credential_ref,
                   team_id,
                   workspace_id,
                   provider,
                   model,
                   base_url,
                   api_key_secret_ref,
                   status
            FROM provider_credentials
            WHERE team_id = ?
              AND workspace_id = ?
            ORDER BY credential_ref
            """,
        CREDENTIAL_ROW_MAPPER,
        teamId,
        workspaceId
    );
  }

  public Optional<ProviderCredentialMetadata> disableWorkspaceCredential(
      String teamId,
      String workspaceId,
      String credentialRef
  ) {
    int updated = jdbcTemplate.update(
        """
            UPDATE provider_credentials
            SET status = 'DISABLED'
            WHERE team_id = ?
              AND workspace_id = ?
              AND credential_ref = ?
            """,
        teamId,
        workspaceId,
        credentialRef
    );
    if (updated == 0) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
              SELECT id,
                     credential_ref,
                     team_id,
                     workspace_id,
                     provider,
                     model,
                     base_url,
                     api_key_secret_ref,
                     status
              FROM provider_credentials
              WHERE team_id = ?
                AND workspace_id = ?
                AND credential_ref = ?
              LIMIT 1
              """,
          CREDENTIAL_ROW_MAPPER,
          teamId,
          workspaceId,
          credentialRef
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  private static ProviderCredentialMetadata mapCredential(ResultSet resultSet, int rowNum) throws SQLException {
    return new ProviderCredentialMetadata(
        resultSet.getString("id"),
        resultSet.getString("credential_ref"),
        resultSet.getString("team_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("provider"),
        resultSet.getString("model"),
        resultSet.getString("base_url"),
        resultSet.getString("api_key_secret_ref"),
        resultSet.getString("status")
    );
  }

  public record ProviderCredentialMetadata(
      String id,
      String credentialRef,
      String teamId,
      String workspaceId,
      String provider,
      String model,
      String baseUrl,
      String apiKeySecretRef,
      String status
  ) {
  }
}
