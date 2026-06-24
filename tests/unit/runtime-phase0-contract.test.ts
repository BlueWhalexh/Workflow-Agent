import { describe, expect, it } from "vitest";
import {
  AgentRunRequestSchema,
  type AgentRuntimeAdapter,
  type KnowledgeChangeset,
  type Note,
  type Ruleset,
  type Vault,
  type Workspace
} from "../../src/runtime/core/types.js";
import { selectRuntimeAdapter } from "../../src/runtime/core/dispatcher.js";
import { WorkspaceService } from "../../src/runtime/core/workspace-service.js";
import {
  AgentEventEnvelopeSchema,
  agentEventTypes,
  createAgentEventEnvelope
} from "../../src/runtime/events/envelope.js";

describe("Phase 0 runtime contract baseline", () => {
  it("models workspace domain and supports both runtime engines", () => {
    const workspace: Workspace = {
      id: "ws_demo",
      name: "Demo Vault",
      currentRevision: "rev_demo_001",
      vaultId: "vault_demo",
      rulesetVersion: "sha256:ruleset"
    };
    const vault: Vault = {
      id: "vault_demo",
      workspaceId: workspace.id,
      kind: "obsidian-compatible-markdown-vault",
      rootRef: "private:vault_demo"
    };
    const note: Note = {
      path: "Inbox/2026-06-01-atlas-search-kickoff.md",
      contentHash: "sha256:note",
      revision: workspace.currentRevision,
      frontmatter: { title: "Atlas Search kickoff notes" }
    };
    const ruleset: Ruleset = {
      version: workspace.rulesetVersion,
      writableRoots: ["Inbox/", "Projects/"],
      requiredFrontmatter: ["title", "project", "type", "date", "status", "source"],
      maxOperationsPerChangeset: 24,
      maxBytesPerChangeset: 120000
    };
    const changeset: KnowledgeChangeset = {
      id: "cs_demo",
      workspaceId: workspace.id,
      sessionId: "sess_demo",
      runId: "run_demo",
      baseRevision: workspace.currentRevision,
      rulesetVersion: ruleset.version,
      proposedAt: "2026-06-24T00:00:00.000Z",
      proposedByEngine: "native-loop",
      status: "CANDIDATE",
      operations: [
        {
          kind: "update",
          path: note.path,
          baseContentHash: note.contentHash,
          content: "---\ntitle: Atlas Search kickoff notes\n---\n"
        }
      ],
      validatorReport: { passed: true, failures: [] },
      sources: [{ notePath: note.path, range: { startLine: 1, endLine: 8 } }]
    };

    expect(vault.workspaceId).toBe(workspace.id);
    expect(changeset.proposedByEngine).toBe("native-loop");
    const [operation] = changeset.operations;
    expect(operation?.kind).toBe("update");
    if (operation?.kind !== "update") {
      throw new Error("Expected update operation");
    }
    expect(operation.baseContentHash).toBe(note.contentHash);
  });

  it("accepts the Phase 0 AgentRunRequest shape and rejects legacy cwd", () => {
    const valid = AgentRunRequestSchema.parse({
      workspaceId: "ws_demo",
      workspaceRevision: "rev_demo_001",
      engine: "native-loop",
      model: { provider: "anthropic", model: "claude-sonnet-4-6" },
      evidence: "mock",
      toolProfile: "knowledge-curator",
      message: "整理 Inbox/",
      budget: {
        maxModelCalls: 24,
        maxToolCalls: 48,
        wallClockMs: 120000,
        toolErrorRetry: 1,
        consecutiveAssistantNoToolLimit: 3
      }
    });

    expect(valid.workspaceId).toBe("ws_demo");
    expect(valid.engine).toBe("native-loop");
    expect(valid.evidence).toBe("mock");

    const legacy = AgentRunRequestSchema.safeParse({
      cwd: "legacy-local-path",
      prompt: "Inspect this repo",
      runtime: "claude-agent-sdk"
    });
    expect(legacy.success).toBe(false);
  });

  it("selects default and per-run runtime adapters without implicit SDK fallback", async () => {
    const native: AgentRuntimeAdapter = {
      engine: "native-loop",
      run: async () => ({ terminalEvent: "run_completed", candidateChangesetIds: [] })
    };
    const sdk: AgentRuntimeAdapter = {
      engine: "claude-agent-sdk",
      run: async () => ({ terminalEvent: "run_completed", candidateChangesetIds: [] })
    };

    expect(selectRuntimeAdapter({ defaultEngine: "native-loop", adapters: [native, sdk] }).engine).toBe("native-loop");
    expect(selectRuntimeAdapter({ defaultEngine: "native-loop", adapters: [native, sdk] }, "claude-agent-sdk").engine).toBe("claude-agent-sdk");
    expect(() => selectRuntimeAdapter({ defaultEngine: "claude-agent-sdk", adapters: [native] })).toThrow(
      /No runtime adapter registered/
    );
  });

  it("resolves workspaceId to private paths without exposing paths in public metadata", () => {
    const service = new WorkspaceService([
      {
        workspaceId: "ws_demo",
        vaultRoot: "private-vault-root/demo-vault",
        currentRevision: "rev_demo_001",
        rulesetVersion: "sha256:ruleset"
      }
    ]);

    expect(service.resolvePrivateWorkspace("ws_demo").vaultRoot).toContain("private-vault-root");
    expect(service.getPublicWorkspace("ws_demo")).toEqual({
      workspaceId: "ws_demo",
      revision: "rev_demo_001",
      rulesetVersion: "sha256:ruleset"
    });
    expect(JSON.stringify(service.getPublicWorkspace("ws_demo"))).not.toContain("private-vault-root");
  });

  it("defines the closed AgentEventEnvelope schema including context_compacted", () => {
    expect(agentEventTypes).toContain("context_compacted");
    expect(agentEventTypes).toContain("run_completed");
    expect(agentEventTypes).not.toContain("observe");

    const envelope = createAgentEventEnvelope({
      eventId: "evt_demo",
      sequence: 1,
      occurredAt: "2026-06-24T00:00:00.000Z",
      sessionId: "sess_demo",
      runId: "run_demo",
      engine: "native-loop",
      evidence: "mock",
      type: "tool_finished",
      payload: {
        toolCallId: "tool_demo",
        outcome: "ok",
        resultPreview: "read_note returned note summary"
      }
    });

    expect(AgentEventEnvelopeSchema.parse(envelope).schemaVersion).toBe(1);
    expect(JSON.stringify(envelope)).not.toContain("rawProviderPayload");
    expect(JSON.stringify(envelope)).not.toContain("private-vault-root");

    expect(
      AgentEventEnvelopeSchema.safeParse({
        ...envelope,
        type: "provider_native_blocks",
        payload: { rawProviderPayload: [] }
      }).success
    ).toBe(false);
  });
});
