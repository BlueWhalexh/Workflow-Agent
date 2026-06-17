ALTER TABLE audit_events
  ADD COLUMN record_digest VARCHAR(71) NULL,
  ADD COLUMN previous_record_digest VARCHAR(71) NULL,
  ADD COLUMN chain_digest VARCHAR(71) NULL,
  ADD COLUMN signature_kind VARCHAR(64) NULL,
  ADD COLUMN signature_value VARCHAR(128) NULL;
