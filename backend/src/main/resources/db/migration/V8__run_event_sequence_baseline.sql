ALTER TABLE run_events
  ADD COLUMN event_sequence BIGINT NOT NULL AUTO_INCREMENT,
  ADD UNIQUE KEY uq_run_events_sequence (event_sequence),
  ADD INDEX idx_run_events_run_sequence (run_id, event_sequence);
