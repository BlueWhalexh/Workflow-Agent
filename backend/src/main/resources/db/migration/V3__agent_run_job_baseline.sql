CREATE TABLE agent_runs (
  id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  requested_by_user_id VARCHAR(64) NOT NULL,
  user_message TEXT NOT NULL,
  mode VARCHAR(64) NOT NULL,
  execute_requested BOOLEAN NOT NULL DEFAULT FALSE,
  auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(64) NOT NULL,
  output_kind VARCHAR(64) NULL,
  display_text TEXT NULL,
  requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
  requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
  wrote_workspace BOOLEAN NOT NULL DEFAULT FALSE,
  artifact_refs JSON NOT NULL,
  target_workspace_paths JSON NOT NULL,
  error_code VARCHAR(128) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_agent_runs_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
  CONSTRAINT fk_agent_runs_requested_by
    FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
  INDEX idx_agent_runs_workspace_id (workspace_id),
  INDEX idx_agent_runs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_jobs (
  id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  available_at TIMESTAMP(6) NOT NULL,
  locked_by VARCHAR(128) NULL,
  locked_until TIMESTAMP(6) NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 3,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_agent_jobs_run
    FOREIGN KEY (run_id) REFERENCES agent_runs (id),
  INDEX idx_agent_jobs_run_id (run_id),
  INDEX idx_agent_jobs_status_available (status, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE run_attempts (
  id VARCHAR(128) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  worker_kind VARCHAR(64) NOT NULL,
  started_at TIMESTAMP(6) NOT NULL,
  finished_at TIMESTAMP(6) NULL,
  status VARCHAR(64) NOT NULL,
  error_code VARCHAR(128) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_run_attempts_run
    FOREIGN KEY (run_id) REFERENCES agent_runs (id),
  CONSTRAINT fk_run_attempts_job
    FOREIGN KEY (job_id) REFERENCES agent_jobs (id),
  INDEX idx_run_attempts_run_id (run_id),
  INDEX idx_run_attempts_job_id (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
