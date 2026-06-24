# SDK Extract Checklist

Phase: 0
Task: P0-T6
Evidence label: manual/source-audit
Constitution: P5, P6, P7, P8, P9, P10

Source of truth:
- ADR: `docs/adr/2026-06-24-sdk-runtime-extract-and-rebuild.md`
- Frozen source: `src/sdk/agent-runtime/`
- Target root: `src/runtime/`

This checklist audits the current worktree line numbers. It does not modify `src/sdk/agent-runtime/`; that directory remains a frozen reference asset.

## Summary

| Source file | Current LOC | Decision | Target | Test migration |
| --- | ---: | --- | --- | --- |
| `src/sdk/agent-runtime/event-normalizer.ts` | 467 | Extract with schema rewrite | `src/runtime/events/normalizer.ts` | migrate selected assertions from `tests/unit/agent-runtime-contract.test.ts` to `tests/unit/runtime-event-normalizer.test.ts` |
| `src/sdk/agent-runtime/safety.ts` | 79 | Extract mostly as redaction core | `src/runtime/events/redaction.ts` | migrate redaction assertions to `tests/unit/runtime-redaction.test.ts` |
| `src/sdk/agent-runtime/claude-agent-runtime-adapter.ts` | 1068 | Extract options/policy/id-mapping only | `src/runtime/claude-sdk/{options,policy-bridge,id-mapping}.ts` | migrate SDK option and deny-matrix assertions to `tests/unit/claude-sdk-options.test.ts` and integration deny matrix |
| `src/sdk/agent-runtime/artifacts.ts` | 299 | Split event store vs artifact store concepts | `src/runtime/events/store.ts`, `src/runtime/artifact/store.ts` | migrate artifact path/store behavior to new EventStore/ArtifactStore tests |
| `src/sdk/agent-runtime/fake-claude-agent-sdk.ts` | 63 | Reuse stream fixture pattern, not write-tool behavior | `src/runtime/claude-sdk/__tests__/fixtures/` | migrate fake stream data into capability fixture loader |
| `src/sdk/agent-runtime/types.ts` | 187 | Do not extract | `src/runtime/core/types.ts` already rebuilt | replaced by Phase 0 contract tests |
| `src/sdk/agent-runtime/index.ts` | 361 | Do not extract public entry | `src/runtime/core/dispatcher.ts` and engine-specific entrypoints | replaced by dispatcher contract tests |
| `src/sdk/agent-runtime/claude-agent-sdk-credentials.ts` | 44 | Keep as historical helper only | none in Phase 0 | future real-provider preflight must avoid public env-name leakage in evidence |
| `src/sdk/agent-runtime/claude-agent-sdk-smoke-scenarios.ts` | 149 | Reuse scenario intent only | `docs/evidence/mvp-release-capability-scenarios.md` / eval fixtures | map historical A-L cases to C1-C11/N1-N2 during Phase 6 |

## Detailed Extraction Map

### `event-normalizer.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 12-28 | `src/runtime/events/normalizer.ts` | medium | Keep stateful normalizer idea, but output `AgentEventEnvelope` payload drafts instead of legacy `AgentRuntimeEvent`. | New test: normalizer maps SDK assistant/tool/result messages to EV-2 payload shapes. |
| 50-119 | `src/runtime/events/normalizer.ts` | medium | Keep content block traversal; drop legacy event names that are not in EV-1. | Assert closed type set; no `observe`, no provider-native block event. |
| 121-180 | `src/runtime/events/normalizer.ts` | low | Replace `permission_request`, `subagent_*`, `checkpoint`, `hook` legacy events with MVP events: `request_approval`, `changeset_*`, `context_compacted`, `plan_updated` where applicable. | Add contract tests for `request_approval` payload summary and `context_compacted`. |
| 197-267 | `src/runtime/events/normalizer.ts` | high | Reuse tool/result/usage parsing; map to `tool_call_started`, `tool_finished`, `model_call_finished` payloads; allocate MyWorkflow `toolCallId`, never expose native `toolUseId`. | Migrate existing tool-use/result summary tests and add native-id non-leak assertion. |
| 270-351 | `src/runtime/events/normalizer.ts` | medium | Reuse status and summarization helpers; map failures to RT-7 `NormalizedError.category`. | Add category mapping tests for provider/tool/policy/validator/budget/cancel/internal. |
| 374-467 | `src/runtime/events/normalizer.ts` and `src/runtime/events/redaction.ts` | medium | Reuse risk classification only as private adapter diagnostic; public envelope uses closed `toolName` from ToolProfile. Move unsafe marker scan behind EventStore redaction audit. | Add P5 tests for local path, auth-shaped values, raw provider payload, and native IDs. |

### `safety.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 1-19 | `src/runtime/events/redaction.ts` | high | Keep sensitive key and text patterns; add Windows paths and explicit environment-name redaction before public evidence use. | Redaction unit tests for local-path-shaped strings, provider credential env-name-shaped strings, bearer/cookie/API-key text. |
| 21-37 | `src/runtime/events/redaction.ts` | high | Keep field classifier; ensure it is called from `EventStore.append()` as the single P5 chokepoint. | Test nested raw provider payload removal. |
| 39-79 | `src/runtime/events/redaction.ts` | high | Reuse recursive redaction; preserve circular-safe behavior; return envelope-safe JSON values. | Port existing circular object and secret tests from `agent-runtime-contract.test.ts`. |

### `claude-agent-runtime-adapter.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 33-84 | `src/runtime/claude-sdk/options.ts` | medium | Rebuild option DTO around new `AgentRunRequest` (`workspaceId`, `workspaceRevision`, `engine`, `model`, `evidence`, `toolProfile`, `budget`). Do not accept `cwd`, `allowedTools`, `mcpServers`, `systemPrompt`, or `settingSources` from public request. | Type/schema tests must reject legacy `cwd` and public custom tool fields. |
| 86-120 | `src/runtime/claude-sdk/options.ts` | medium | Reuse option construction shape, but force `settingSources: []`, `strictMcpConfig: true`, `mcpServers` from sidecar config only, and MyWorkflow semantic tools only. Remove direct `cwd: request.cwd`. | Snapshot test locks forced SDK safety fields and proves config override cannot remove them. |
| 122-130 | no direct reuse | none | Do not expose `allowDangerouslySkipPermissions`, legacy session resume, or SDK native session branching from public request. | Negative tests for forbidden public fields. |
| 325-338 | `src/runtime/claude-sdk/options.ts` | high | Keep explicit generic write-tool deny list; make it unconditional for SDK adapter, not only read-only mode. Include `Write`, `Edit`, `MultiEdit`, `NotebookEdit`, `Bash`. | Deny-matrix tests for generic write/command tools. |
| 340-380 | `src/runtime/claude-sdk/policy-bridge.ts` | medium | Rewire `canUseTool` to MyWorkflow `PolicyEngine`; approval request must use PA-4 structured approval schema, not free-text callback. | Integration test: `canUseTool=false` prevents SDK tool execution; approval is bound to `approvalId/action/scope/changesetId/expiresAt`. |
| 481-518 | `src/runtime/claude-sdk/options.ts` or `events/redaction.ts` | medium | Reuse input summarization only for adapter-private diagnostics; public args go through EV-2 `argsPreview`/artifact ref. | Args preview limit and redaction tests. |
| 518-566 | `src/runtime/tools/tool-policy.ts` or `claude-sdk/options.ts` | medium | Reuse risk classification as deny-list support; runtime public tool surface remains manifest-driven `ToolProfile`. | Test tool profile cannot be replaced by front-end-provided tool list. |
| 644-768 | `src/runtime/claude-sdk/adapter.ts` | low | Stream handling pattern can inform adapter, but event emission must go through `RuntimeHooks.emitEvent()` and EV-1 envelope. No direct artifact writes. | Adapter fixture tests over normalized envelope sequence. |
| 919-970 | no direct reuse | none | Legacy workspace path normalization depends on `cwd`; replace with `WorkspaceService.resolvePrivateWorkspace(workspaceId)` and P5 public metadata. | WorkspaceService tests for private path resolution and public path non-leak. |
| 1061-1068 | `src/runtime/claude-sdk/options.ts` | medium | Keep final risk helper only if needed internally; never expose raw SDK `toolUseID`. | Native ID isolation tests. |

### `artifacts.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 13-43 | `src/runtime/events/store.ts` and `src/runtime/artifact/store.ts` | low | Replace legacy run artifact schema with EventStore/ArtifactStore tables and EV-1 envelope. Remove `cwdHash` public relevance. | EventStore append/replay tests; ArtifactStore inline/ref tests. |
| 55-64 | `src/runtime/artifact/store.ts` | medium | Reuse safe run-relative path idea only for internal artifact blob keys; public API uses `artifactId`. | Tests for deterministic artifact IDs and no path leakage. |
| 66-120 | no Phase 0 extraction | none | Workspace snapshot/rollback is unsafe for new P1 changeset boundary; future apply pipeline must use changeset staging, not SDK workspace rollback. | Covered by future P1 stale/apply tests, not Phase 0. |
| 225-241 | `src/runtime/events/store.ts` / `src/runtime/artifact/store.ts` | low | Replace file JSON artifact writes with sqlite WAL append and blob storage per EV-4/OQ-EV-4. | Store tests write/read/replay; redaction at append. |

### `fake-claude-agent-sdk.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 7-16 | `src/runtime/claude-sdk/__tests__/fixtures/fake-streams.ts` | high | Keep async iterable fixture pattern; replace legacy request shape with runtime request. | Fixture loader test. |
| 17-62 | `src/runtime/claude-sdk/__tests__/fixtures/` | medium | Reuse event sequencing idea, but replace `Write` fixture with MyWorkflow semantic tools (`search_notes`, `read_note`, `propose_changeset`, `validate_changeset`) and no direct write/checkpoint. | Capability fixture tests for C1-C11/N1-N2 shapes. |

### `types.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 3-187 | none | 0% | Do not extract. Legacy DTO is centered on `cwd`, `permissionMode`, custom tools, subagents, MCP, checkpoint, and SDK-native IDs. Phase 0 rebuilds contract in `src/runtime/core/types.ts`. | `tests/unit/runtime-phase0-contract.test.ts` replaces the contract baseline for Phase 0. |

### `index.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 1-149 | none | low | Do not extract public factory. New dispatcher is engine-agnostic and must not default to SDK when no SDK adapter is registered. | Dispatcher unit tests. |
| 149-170 | `src/runtime/events/normalizer.ts` / `redaction.ts` | medium | Redaction call pattern is useful, but must move to `EventStore.append()` single chokepoint. | EventStore redaction tests. |
| 171-361 | none | low | Legacy result/run artifact shape is not part of MVP public contract. | Replaced by envelope and run outcome tests. |

### `claude-agent-sdk-credentials.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 1-44 | no Phase 0 target | low | Historical credential preflight only. Future real-provider smoke can reuse concept, but P5 forbids env-name leakage in public DTO/event/evidence. | Future Phase 3/6 smoke tests must label `real external` and redact credential names in public artifacts. |

### `claude-agent-sdk-smoke-scenarios.ts`

| Lines | New location | Reuse | Required adaptation | Test migration |
| --- | --- | ---: | --- | --- |
| 1-149 | eval fixture docs/scripts | medium | Reuse scenario intent only. Map historical A-L SDK parity cases to MVP capability scenarios C1-C11 and negative N1-N2; do not carry old SDK-centric success criteria into MVP Done. | Phase 6 eval suite tests. |

## Reviewer Checks

- No Phase 0 implementation files are added under `src/sdk/agent-runtime/`.
- New runtime contract code is under `src/runtime/`.
- Extraction targets preserve P5 public boundary hygiene: no public `cwd`, local path, env name, raw provider payload, or native SDK IDs.
- Extraction targets preserve P7/P8: SDK remains adapter-only; no front-end-provided MCP/custom tool/arbitrary Bash surface.
- Test migration paths distinguish `unit/mock`, `integration/local`, and future `real external` evidence.
