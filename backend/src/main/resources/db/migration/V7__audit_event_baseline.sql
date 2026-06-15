CREATE TABLE audit_events (
  id VARCHAR(64) NOT NULL,
  actor_user_id VARCHAR(64) NOT NULL,
  team_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NULL,
  event_type VARCHAR(64) NOT NULL,
  message VARCHAR(255) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_audit_events_actor
    FOREIGN KEY (actor_user_id) REFERENCES users (id),
  CONSTRAINT fk_audit_events_team
    FOREIGN KEY (team_id) REFERENCES teams (id),
  CONSTRAINT fk_audit_events_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
  CONSTRAINT fk_audit_events_run
    FOREIGN KEY (run_id) REFERENCES agent_runs (id),
  INDEX idx_audit_events_run_created (run_id, created_at, id),
  INDEX idx_audit_events_workspace_created (workspace_id, created_at, id),
  INDEX idx_audit_events_actor_created (actor_user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
