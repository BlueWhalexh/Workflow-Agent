# Java External Directory Sync Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a thin Java backend external directory snapshot sync baseline so frontend/admin integrations can import team members through the backend without raw secrets or runtime-specific payloads.

**Architecture:** Reuse the existing team directory repository and permission model. Add a service-owned sync method that accepts structured member snapshots, rejects raw secret/payload aliases, requires the current principal to be an active team admin, optionally disables missing members while protecting the current admin, and advertises the new endpoint in the ops integration contract. Team-scoped audit is explicitly left as a follow-up because the current audit schema is workspace-scoped with a non-null workspace foreign key.

**Tech Stack:** Spring Boot MVC, existing Java backend API envelope, existing in-memory/JDBC workspace repositories, existing audit repository, Maven/JUnit/MockMvc.

---

### Task 1: RED tests for directory sync API and contract

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/identity/TeamDirectorySyncControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`

- [x] **Step 1: Write failing tests**

Create tests that assert:
- `POST /v1/teams/{teamId}/directory-sync` imports structured external members.
- Non-admin users receive `TEAM_FORBIDDEN`.
- Raw `token`, `authorization`, `Authorization`, `secret`, and `rawPayload` aliases are rejected and not echoed.
- `disableMissing=true` disables missing members but never disables the current admin.
- `/v1/ops/integration-contract` advertises `POST /v1/teams/{teamId}/directory-sync` and `externalDirectorySync=true`.

- [x] **Step 2: Run focused RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=TeamDirectorySyncControllerTest,OpsIntegrationContractControllerTest
```

Expected before implementation: fail because the route is not present and the ops contract still reports `externalDirectorySync=false`.

### Task 2: Directory sync service and controller

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamDirectorySyncService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`

- [x] **Step 1: Implement minimal service**

Add `syncCurrentTeamDirectory(teamId, source, disableMissing, members)` that:
- Normalizes team id, source, user id, display name, and role.
- Requires active `TEAM_ADMIN` for the current principal.
- Rejects empty member snapshots.
- Upserts each snapshot member as active.
- If `disableMissing=true`, disables active known members missing from the snapshot, excluding the current principal.
- Returns imported/disabled counts plus sorted public member records.
- Does not write an audit event until the backend has a team-scoped audit schema; the current audit table requires a workspace id foreign key.

- [x] **Step 2: Add controller route**

Add:

```http
POST /v1/teams/{teamId}/directory-sync
```

with request fields:
- `source`
- `disableMissing`
- `members[]`
- raw aliases `token`, `authorization`, `Authorization`, `secret`, `rawPayload` for rejection only

and response fields:
- `teamId`
- `source`
- `importedCount`
- `disabledCount`
- `members`

### Task 3: Ops contract and docs

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/superpowers/plans/2026-06-17-java-external-directory-sync-baseline.md`

- [x] **Step 1: Update contract**

Add frontend endpoint `POST /v1/teams/{teamId}/directory-sync` and set `externalDirectorySync=true`.

- [x] **Step 2: Update delivery/audit report**

Document J40A as a backend-owned external directory snapshot sync baseline, with explicit remaining gaps:
- No SCIM/LDAP/IdP connector.
- No scheduler.
- No external directory credential storage.
- No production directory claim reconciliation beyond request-time principal claims.
- No team-scoped audit event storage for directory sync; current audit events remain workspace-scoped.

### Task 4: Verification and commit

**Files:**
- Verify all modified files.

- [x] **Step 1: Focused tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=TeamDirectorySyncControllerTest,OpsIntegrationContractControllerTest
```

- [x] **Step 2: Full backend tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 3: Typecheck**

Run:

```bash
npm run typecheck
```

- [x] **Step 4: Static and token scans**

Run:

```bash
git diff --check
git diff --cached --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-external-directory-sync-baseline.md
```

- [x] **Step 5: Commit and push**

Stage only backend J40A files and commit:

```bash
git add backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java backend/src/main/java/com/myworkflow/agent/backend/identity/TeamDirectorySyncService.java backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java backend/src/test/java/com/myworkflow/agent/backend/identity/TeamDirectorySyncControllerTest.java backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java docs/reports/java-backend-phase-one-completion-audit.md docs/superpowers/plans/2026-06-17-java-external-directory-sync-baseline.md
git commit -m "feat: add external directory sync baseline"
git push
```
