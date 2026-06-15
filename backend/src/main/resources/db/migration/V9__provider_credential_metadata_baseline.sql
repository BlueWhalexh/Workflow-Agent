CREATE TABLE provider_credentials (
  id VARCHAR(64) NOT NULL,
  credential_ref VARCHAR(64) NOT NULL,
  team_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NULL,
  base_url VARCHAR(512) NULL,
  api_key_secret_ref VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_credentials_team_ref (team_id, credential_ref),
  CONSTRAINT fk_provider_credentials_team
    FOREIGN KEY (team_id) REFERENCES teams (id),
  CONSTRAINT fk_provider_credentials_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
  INDEX idx_provider_credentials_workspace (workspace_id),
  INDEX idx_provider_credentials_provider_status (provider, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
