package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkspaceSchemaArtifactTest {

  @Test
  void migrationDefinesIdentityAndWorkspaceBaseline() throws Exception {
    String migration = Files.readString(Path.of(
        "src/main/resources/db/migration/V2__identity_workspace_baseline.sql"
    )).toLowerCase();
    String teamMemberLifecycleMigration = Files.readString(Path.of(
        "src/main/resources/db/migration/V12__team_member_lifecycle_baseline.sql"
    )).toLowerCase();

    assertThat(migration).contains("create table users");
    assertThat(migration).contains("create table teams");
    assertThat(migration).contains("create table team_memberships");
    assertThat(migration).contains("create table workspaces");
    assertThat(migration).contains("create table workspace_members");
    assertThat(migration).contains("constraint fk_team_memberships_team");
    assertThat(migration).contains("constraint fk_team_memberships_user");
    assertThat(migration).contains("constraint fk_workspaces_team");
    assertThat(migration).contains("constraint fk_workspace_members_workspace");
    assertThat(migration).contains("constraint fk_workspace_members_user");
    assertThat(migration).contains("index idx_workspaces_team_id");
    assertThat(migration).contains("index idx_workspace_members_user_id");
    assertThat(teamMemberLifecycleMigration).contains("alter table team_memberships");
    assertThat(teamMemberLifecycleMigration).contains("add column status");
    assertThat(teamMemberLifecycleMigration).contains("idx_team_memberships_status");
  }
}
