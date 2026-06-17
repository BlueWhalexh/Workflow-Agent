# Java Remote Runner Dispatch Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend-owned registered remote runner dispatch baseline so an agent run can explicitly target a known workspace remote runner through `remoteRunnerRef`.

**Architecture:** Keep the existing async run/job model and remote HTTP result envelope. Add an optional `remoteRunnerRef` request field; when present, resolve the runner from the JDBC remote runner registry, require it to be online and capability-compatible, dynamically create a `RemoteHttpAgentWorker` for its endpoint, and execute the run through that worker. This is not a production runner platform: no runner identity, mTLS, runner-scoped secret distribution, remote cancellation, artifact upload callback, automatic scheduler, or multi-node fanout.

**Tech Stack:** Spring Boot MVC, existing JDBC remote runner repository, existing `RemoteHttpAgentWorker`, JUnit/MockMvc/Testcontainers, TypeScript typecheck.

---

### Task 1: RED tests for explicit remote runner dispatch

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteRunnerDispatchControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`

- [x] **Step 1: Write failing tests**

Test the desired public behavior:
- Owner registers a workspace remote runner with a local HTTP stub endpoint.
- Owner heartbeats the runner so it becomes `ONLINE`.
- Owner creates an agent run with `remoteRunnerRef`.
- Backend calls the registered endpoint, records the backend response, and persists attempt `workerKind=REMOTE_RUNNER`.
- The run response and runner metadata do not include runner secrets, signature secrets, tokens, Authorization material, or provider API keys.
- Creating a run with a registered but not-online runner returns `VALIDATION_ERROR` before creating a queued run.
- `/v1/ops/integration-contract` reports `remoteRunnerDispatch=true`.

- [x] **Step 2: Run focused RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=RemoteRunnerDispatchControllerTest,OpsIntegrationContractControllerTest
```

Expected before implementation: fail because `remoteRunnerRef` is ignored by `CreateAgentRunRequest` / `AgentRunService`, and the ops contract still reports `remoteRunnerDispatch=false`.

### Task 2: Implement dispatch resolver

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/run/RemoteRunnerDispatchService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunController.java`

- [x] **Step 1: Add `RemoteRunnerDispatchService`**

Responsibilities:
- Accept `workspaceId` and optional `remoteRunnerRef`.
- Return empty when `remoteRunnerRef` is absent.
- Require JDBC `RemoteRunnerRepository` when `remoteRunnerRef` is present.
- Find the runner within the same workspace.
- Require `RemoteRunnerStatus.ONLINE`.
- Require capability `agent-backend-response.v1`.
- Create `RemoteHttpAgentWorker` using the runner endpoint, existing remote HTTP timeout config, existing optional signature-secret config, and active profiles.
- Do not read or persist raw runner secrets.

- [x] **Step 2: Wire `AgentRunService`**

Responsibilities:
- Add `remoteRunnerRef` to `startRun`.
- Resolve the selected worker before secret-injection validation.
- Preserve the existing default worker path when `remoteRunnerRef` is absent.
- Continue recording `workerKind` from the selected worker in attempts.
- Keep provider credential secret injection blocked for workers that do not support it.

- [x] **Step 3: Wire controller request**

Responsibilities:
- Add optional `remoteRunnerRef` to `CreateAgentRunRequest`.
- Pass it to `AgentRunService.startRun`.
- Do not add runner endpoint or secret fields to public run response.

### Task 3: Contract and docs

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-17-java-remote-runner-dispatch-baseline.md`

- [x] **Step 1: Update ops contract**

Set `remoteRunnerDispatch=true` after explicit registered-runner dispatch exists.

- [x] **Step 2: Update docs and report**

Document J41A boundaries:
- Explicit `remoteRunnerRef` only.
- No production runner identity or mTLS.
- No runner-scoped secret distribution.
- No remote artifact upload callback.
- No remote cancellation callback.
- No automatic scheduler or multi-node fanout.
- Local HTTP stubs are fake integration evidence, not a real runner platform smoke.

### Task 4: Verification and commit

**Files:**
- Verify all modified files.

- [x] **Step 1: Focused tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=RemoteRunnerDispatchControllerTest,RemoteRunnerControllerTest,RemoteHttpAgentWorkerControllerTest,OpsIntegrationContractControllerTest
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
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-remote-runner-dispatch-baseline.md
```

- [x] **Step 5: Commit and push**

Stage only J41A files and commit:

```bash
git add backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunController.java backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java backend/src/main/java/com/myworkflow/agent/backend/run/RemoteRunnerDispatchService.java backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java backend/src/test/java/com/myworkflow/agent/backend/run/RemoteRunnerDispatchControllerTest.java backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java docs/architecture/java-team-backend-platform-spec.md docs/reports/java-backend-phase-one-completion-audit.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/superpowers/plans/2026-06-17-java-remote-runner-dispatch-baseline.md
git commit -m "feat: add registered remote runner dispatch baseline"
git push
```
