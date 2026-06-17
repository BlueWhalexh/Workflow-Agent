# Java Team Invite Onboarding Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local team invite/onboarding baseline so an active team admin can create, list, revoke, and let the invited user accept a current-team invite without introducing email, external IAM, or raw invite secrets.

**Architecture:** Keep invites in the Java identity control plane as local metadata. The controller exposes current-team invite APIs; a service enforces active `TEAM_ADMIN` creation/list/revoke and invitee-only accept; repository implementations persist invite state in memory and JDBC. Accepting an invite upserts an active backend-known team member through the existing team directory boundary.

**Tech Stack:** Java 21, Spring Boot MVC, Spring JDBC, Flyway, JUnit 5, MockMvc, Testcontainers.

---

## Scope

In:

- Current-team local invite lifecycle only.
- Invite states: `PENDING`, `ACCEPTED`, `REVOKED`.
- Public response contains invite metadata and no token, secret, raw provider payload, workspace path, or server storage ref.
- Active team admin can create/list/revoke invites.
- The invited user can accept their own pending invite; accepting creates an active team membership.
- Default-profile MockMvc tests and JDBC/Testcontainers smoke.

Out:

- Email delivery, invite links, bearer invite tokens, external IAM/OIDC directory sync, session onboarding UI.
- Cross-team invite acceptance.
- Global team CRUD.
- Production KMS/Keychain/Vault or remote runner secret distribution.

## Files

- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamInviteRecord.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamInviteStatus.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamInviteRepository.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/InMemoryTeamInviteRepository.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/JdbcTeamInviteRepository.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamInviteService.java`
- Create: `backend/src/main/resources/db/migration/V13__team_invites.sql`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/identity/TeamInviteControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceJdbcControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceSchemaArtifactTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan, checking off completed steps.

## Task 1: RED Controller Contract

**Files:**

- Create: `backend/src/test/java/com/myworkflow/agent/backend/identity/TeamInviteControllerTest.java`

- [x] **Step 1: Write failing MockMvc tests**

Create tests that exercise the intended API before production code exists:

```java
@Test
void adminCreatesListsAndInviteeAcceptsInviteWithoutSecretFields() throws Exception {
  // Arrange: create a workspace as team-admin so a persisted active admin exists.
  // Act: POST /v1/teams/team-invite-test/invites with inviteeUserId/displayName/role.
  // Assert: response has status PENDING and no token/secret/workspaceRoot/serverStorageRef fields.
  // Act: GET /v1/teams/team-invite-test/invites as admin.
  // Assert: pending invite is visible.
  // Act: POST /v1/teams/team-invite-test/invites/{inviteId}/accept as invited user.
  // Assert: response has status ACCEPTED.
  // Act: GET /v1/teams/team-invite-test/members as admin.
  // Assert: invitee is ACTIVE TEAM_MEMBER.
}

@Test
void nonAdminCannotCreateInvite() throws Exception {
  // Arrange: admin creates workspace and grants a member.
  // Act: member POST /v1/teams/team-invite-forbidden/invites.
  // Assert: 403 with TEAM_FORBIDDEN.
}

@Test
void revokedInviteCannotBeAccepted() throws Exception {
  // Arrange: admin creates invite.
  // Act: admin POST /v1/teams/team-invite-revoke/invites/{inviteId}/revoke.
  // Assert: status REVOKED.
  // Act: invited user POST accept.
  // Assert: 400 VALIDATION_ERROR.
}
```

- [x] **Step 2: Run RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=TeamInviteControllerTest
```

Expected before implementation: fail because `/v1/teams/{teamId}/invites` endpoints do not exist.

## Task 2: Implement Invite Domain And HTTP API

**Files:**

- Create the invite domain/repository/service classes listed above.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`.

- [x] **Step 1: Add domain records and repository port**

Add `TeamInviteStatus`, `TeamInviteRecord`, and `TeamInviteRepository` with create/list/find/update operations. Repository methods must store and return only metadata; no raw token or secret field exists in the model.

- [x] **Step 2: Add in-memory repository**

Implement `InMemoryTeamInviteRepository` with deterministic state transitions for default tests. Use generated ids shaped as `ti_<uuid>` and sort list output by `createdAt`, then `id`.

- [x] **Step 3: Add service guard**

Implement `TeamInviteService`:

- Normalize `teamId`, `inviteeUserId`, display name, and role.
- Require current team for all operations.
- Require active `TEAM_ADMIN` for create/list/revoke.
- Reject self-invite.
- Allow only the invited current principal to accept.
- Reject accepting non-pending invites.
- On accept, call `WorkspaceRepository.upsertTeamMember(...)` with an active role.

- [x] **Step 4: Add controller routes**

Add:

```text
POST /v1/teams/{teamId}/invites
GET /v1/teams/{teamId}/invites
POST /v1/teams/{teamId}/invites/{inviteId}/accept
POST /v1/teams/{teamId}/invites/{inviteId}/revoke
```

The response envelope should use `ApiEnvelope.ok(...)` and public invite response fields:

```text
id, teamId, inviteeUserId, displayName, role, status, createdByUserId, createdAt, updatedAt
```

- [x] **Step 5: Run focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=TeamInviteControllerTest,TeamDirectoryLifecycleControllerTest,TeamMemberControllerTest
```

Expected: pass.

## Task 3: JDBC Persistence And Migration Smoke

**Files:**

- Create: `backend/src/main/resources/db/migration/V13__team_invites.sql`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/JdbcTeamInviteRepository.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceJdbcControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceSchemaArtifactTest.java`

- [x] **Step 1: Add Flyway migration**

Create `team_invites` with columns:

```sql
id, team_id, invitee_user_id, display_name, role, status, created_by_user_id, created_at, updated_at
```

Add indexes for `team_id/status` and `invitee_user_id/status`. Keep invitee and creator ids as metadata strings so local invite creation does not depend on a pre-existing external directory user row.

- [x] **Step 2: Add JDBC repository**

Implement `JdbcTeamInviteRepository` using explicit SQL and existing Spring JDBC style.

- [x] **Step 3: Add JDBC controller smoke**

Extend the Testcontainers-backed workspace controller smoke to create and accept an invite through HTTP, proving migration and JDBC repository wiring work under the JDBC profile.

- [x] **Step 4: Update schema artifact expected migration list**

Update the schema artifact test so V13 is included.

- [x] **Step 5: Run JDBC focused tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=WorkspaceJdbcControllerTest,WorkspaceSchemaArtifactTest
```

Expected: pass.

## Task 4: Docs, Reports, Static Verification

**Files:**

- Modify docs listed in Files.

- [x] **Step 1: Update architecture spec**

Add J35A to the current-status line and document that local invite onboarding exists but is not production external directory sync.

- [x] **Step 2: Update completion audit**

Mark invite/onboarding as implemented baseline and keep full OIDC/JWKS/external directory sync as remaining partial gap.

- [x] **Step 3: Update delivery report**

Record RED, focused GREEN, full Java, typecheck, static diff, and token scan evidence.

- [x] **Step 4: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-team-invite-onboarding-baseline.md
```

Expected:

- Java suite passes.
- TypeScript typecheck passes.
- Diff whitespace check passes.
- Token scan has no matches.
- Completed plan has no unchecked steps.

## Review Points

- Correctness: invite state transitions must not create membership before accept.
- Boundary: backend invite response must never expose raw token/secret/link material.
- Permission: create/list/revoke require active team admin; accept requires invited user.
- Data: migration must be additive and not break existing team membership access guards.
- Scope: this is local onboarding metadata, not real OIDC/JWKS or external IAM directory sync.
