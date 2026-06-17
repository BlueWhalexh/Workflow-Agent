ALTER TABLE team_memberships
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER role,
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at;

CREATE INDEX idx_team_memberships_status ON team_memberships (status);
