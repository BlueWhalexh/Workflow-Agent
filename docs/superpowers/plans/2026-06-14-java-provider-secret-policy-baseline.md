# Java Provider Secret Policy Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J9A only; do not implement a credential database, secret manager integration, UI, real provider smoke, runner-scoped secret distribution, or raw token storage.

**Goal:** Add a minimal provider runtime reference policy so Java backend callers can select configured provider runtime references without sending provider token values through the public API, logs, DB, or worker artifacts.

**Architecture:** Public run creation accepts a safe `providerRuntimeRef` string, not a provider runtime object or token value. A backend-owned `ProviderRuntimePolicy` maps that reference to the existing TS `providerRuntime` shape using configured provider metadata and API key environment variable names only. `AgentRunService` sends the resolved runtime map to the worker while keeping the default fake provider behavior unchanged.

**Tech Stack:** Spring MVC request DTO, Spring `Environment`, Java records/maps, JUnit 5, MockMvc.

---

## Scope Check

In scope:

- Add optional public API field `providerRuntimeRef` on `POST /v1/workspaces/{workspaceId}/agent-runs`.
- Reject direct `providerRuntime`, `apiKey`, `token`, `authorization`, or other unknown request fields through existing strict request-body parsing.
- Add `ProviderRuntimePolicy` for backend-owned reference resolution:
  - blank ref keeps current behavior;
  - `fake` is built in;
  - configured refs live under `my-workflow.backend.provider-runtime.refs.<ref>.*`;
  - real provider refs include `apiKeyEnvName`, not API key value.
- Support provider values already supported by TS runtime:
  - `fake`;
  - `deepseek-real`;
  - `mimo-real`;
  - current fixture providers.
- Validate provider runtime ref names with a conservative slug pattern.
- Prove worker requests contain provider id, model/baseUrl/timeout and `apiKeyEnvName`, but never a raw token value.
- Prove public error responses do not echo token-like values from rejected requests.

Out of scope:

- Secret storage, rotation, encryption, or database credential tables.
- Reading secret values in Java.
- Real external provider calls.
- Remote runner secret distribution.
- Public credential management API.
- Audit listing API.
- DB schema changes.

## Files

Production:

- Add `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicy.java`
  - Resolves a safe reference to a `Map<String, Object>` provider runtime.
  - Reads only backend config metadata and API key env var names.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunController.java`
  - Add `providerRuntimeRef` to `CreateAgentRunRequest`.
  - Pass it to `AgentRunService.startRun(...)`.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java`
  - Inject `ProviderRuntimePolicy`.
  - Resolve provider runtime before async execution.
  - Pass resolved provider runtime to `AgentWorkerRequest`.

Tests:

- Add `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicyTest.java`
  - Unit-level checks for blank/default, fake, configured real ref, unknown ref, invalid ref, and no raw token value.
- Add `backend/src/test/java/com/myworkflow/agent/backend/run/ProviderSecretPolicyControllerTest.java`
  - HTTP-level check for safe `providerRuntimeRef` propagation to worker.
  - HTTP-level check that direct token-bearing provider runtime payload is rejected and not echoed.

Docs:

- Update `docs/architecture/java-team-backend-platform-spec.md`.
- Update this plan after execution.
- Update `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Tasks

- [x] Write RED `ProviderRuntimePolicyTest`.
- [x] Write RED `ProviderSecretPolicyControllerTest`.
- [x] Run focused RED:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
- [x] Implement `ProviderRuntimePolicy`.
- [x] Add `providerRuntimeRef` to `AgentRunController.CreateAgentRunRequest`.
- [x] Wire `ProviderRuntimePolicy` into `AgentRunService`.
- [x] Run focused GREEN:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
- [x] Run Java full suite:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
- [x] Run TS suite and typecheck:
  - `npm test`
  - `npm run typecheck`
- [x] Run static gates:
  - `git diff --check`
  - whitespace/merge-marker scan over backend and touched docs.
  - token scan over backend/docs/src/tests.
- [x] Archive execution evidence and boundaries.

## Execution Status

Implemented for Phase J9A.

Delivered:

- Added `ProviderRuntimePolicy`.
- Added optional public `providerRuntimeRef` to agent run creation.
- Backend resolves safe refs to TS-compatible provider runtime metadata.
- Real provider refs pass `apiKeyEnvName`, not API key value.
- Existing default fake provider behavior remains unchanged for `llm-open-agent` and executable `fixed-workflow`.
- Direct token-bearing `providerRuntime` payload remains rejected by strict request parsing.

RED evidence:

- Focused RED failed at Java test compile because `ProviderRuntimePolicy` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 6 tests passed.
  - Covers blank/default fake behavior, configured real provider ref mapping, unknown/invalid ref rejection, safe HTTP propagation to worker, token-value rejection, and no response echo of token-like request data.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 60 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J9A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-secret-policy-baseline.md docs/superpowers/plans/2026-06-14-java-remote-runner-contract-spike.md docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J9A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J9A archive updates.

Boundaries:

- J9A does not store, rotate, encrypt, or retrieve secret values.
- J9A does not add credential database tables or public credential management APIs.
- J9A does not run a real external provider call.
- J9A does not distribute secrets to remote runners; it only passes safe runtime metadata and env var names to the existing worker contract.
- Configured provider refs are local/backend config metadata, not team-scoped credential records.

## Completion Boundary

J9A is complete only if:

- Public API accepts `providerRuntimeRef`, not raw provider runtime config.
- Direct token-bearing request fields are rejected before run creation.
- Rejection responses do not echo token-like values.
- Java resolves configured provider runtime refs without reading secret values.
- Worker request `providerRuntime` can include `apiKeyEnvName`, `provider`, `model`, `baseUrl`, and `timeoutMs`, but never `apiKey`, `token`, or Authorization value.
- Existing default fake provider behavior remains unchanged.
- No production dependency, DB schema, secret storage, or real provider execution is introduced.
