CREATE TABLE team_invites (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  invitee_user_id VARCHAR(64) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  role VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  created_by_user_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_team_invites_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_team_invites_team_status ON team_invites (team_id, status);
CREATE INDEX idx_team_invites_invitee_status ON team_invites (invitee_user_id, status);
