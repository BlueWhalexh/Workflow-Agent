CREATE TABLE run_events (
  id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  message VARCHAR(255) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_run_events_run
    FOREIGN KEY (run_id) REFERENCES agent_runs (id),
  INDEX idx_run_events_run_created (run_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
