CREATE TABLE artifact_refs (
  id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  artifact_ref VARCHAR(1024) NOT NULL,
  kind VARCHAR(64) NOT NULL,
  redaction_status VARCHAR(64) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  sort_order INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_artifact_refs_run
    FOREIGN KEY (run_id) REFERENCES agent_runs (id),
  CONSTRAINT fk_artifact_refs_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
  INDEX idx_artifact_refs_run_id (run_id),
  INDEX idx_artifact_refs_workspace_id (workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
