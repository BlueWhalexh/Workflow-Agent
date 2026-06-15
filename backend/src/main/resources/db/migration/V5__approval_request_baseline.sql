CREATE TABLE approval_requests (
  id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  requested_by_user_id VARCHAR(64) NOT NULL,
  decided_by_user_id VARCHAR(64) NULL,
  decision VARCHAR(64) NULL,
  status VARCHAR(64) NOT NULL,
  artifact_ref VARCHAR(1024) NULL,
  target_workspace_paths JSON NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  decided_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_approval_requests_run
    FOREIGN KEY (run_id) REFERENCES agent_runs (id),
  CONSTRAINT fk_approval_requests_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
  CONSTRAINT fk_approval_requests_requested_by
    FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
  CONSTRAINT fk_approval_requests_decided_by
    FOREIGN KEY (decided_by_user_id) REFERENCES users (id),
  INDEX idx_approval_requests_run_id (run_id),
  INDEX idx_approval_requests_workspace_id (workspace_id),
  INDEX idx_approval_requests_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
