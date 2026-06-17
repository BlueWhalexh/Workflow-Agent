CREATE TABLE remote_runners (
  id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  runner_ref VARCHAR(64) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  endpoint_url VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL,
  capabilities JSON NOT NULL,
  last_seen_at TIMESTAMP(6) NULL,
  lease_owner VARCHAR(128) NULL,
  lease_expires_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_remote_runners_workspace_ref (workspace_id, runner_ref),
  CONSTRAINT fk_remote_runners_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
  INDEX idx_remote_runners_workspace_status (workspace_id, status),
  INDEX idx_remote_runners_lease_expires (workspace_id, lease_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
