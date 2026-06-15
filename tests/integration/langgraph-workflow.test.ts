import { access, cp, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";
import { reportNode } from "../../src/runtime/langgraph/nodes/report-node.js";

let tempRoot: string;

async function fileExists(filePath: string): Promise<boolean> {
  return access(filePath)
    .then(() => true)
    .catch(() => false);
}

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "workflow-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("LangGraph workflow", () => {
  it("stops at plan approval without auto approve", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-test",
      autoApprove: false
    });

    expect(result.status).toBe("WAITING_PLAN_APPROVAL");
    expect(result.planPath).toBe(".agent-runs/run-test/plan.json");
  });

  it("executes mock note agent with auto approve", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-test",
      autoApprove: true
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-test/report.md");
  });

  it("executes with explicit fake provider runtime config", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-provider-runtime",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-provider-runtime/report.md");
  });

  it("executes with DeepSeek fixture provider runtime config", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-deepseek-fixture",
      autoApprove: true,
      providerRuntime: {
        provider: "deepseek-fixture",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-deepseek-fixture/report.md");
  });

  it("executes with MiMo vLLM fixture provider runtime config", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-mimo-vllm-fixture",
      autoApprove: true,
      providerRuntime: {
        provider: "mimo-vllm-fixture",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-mimo-vllm-fixture/report.md");
  });

  it("executes with opt-in DeepSeek real provider through injected fake fetch", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-deepseek-real-injected",
      autoApprove: true,
      providerRuntime: {
        provider: "deepseek-real",
        timeoutMs: 30000,
        model: "deepseek-v4-pro",
        baseUrl: "https://api.deepseek.com",
        apiKeyEnvName: "TEST_DEEPSEEK_API_KEY"
      },
      providerRuntimeDependencies: {
        env: { TEST_DEEPSEEK_API_KEY: "test-api-key" },
        fetch: async (url, init) => {
          requests.push({ url: String(url), init: init ?? {} });
          return new Response(
            JSON.stringify({
              model: "deepseek-v4-pro",
              choices: [
                {
                  finish_reason: "stop",
                  message: {
                    role: "assistant",
                    content: `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

DeepSeek injected fake fetch 生成结构化 note。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Provider 只能作为 adapter。

## 相关链接

暂无相关链接。
`
                  }
                }
              ],
              usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(requests.length).toBeGreaterThan(0);
    expect(requests[0].url).toBe("https://api.deepseek.com/chat/completions");
  });

  it("executes with opt-in MiMo real provider through injected fake fetch", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-mimo-real-injected",
      autoApprove: true,
      providerRuntime: {
        provider: "mimo-real",
        timeoutMs: 30000,
        model: "mimo-test-model",
        baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
        apiKeyEnvName: "TEST_MIMO_API_KEY"
      },
      providerRuntimeDependencies: {
        env: { TEST_MIMO_API_KEY: "test-api-key" },
        fetch: async (url, init) => {
          requests.push({ url: String(url), init: init ?? {} });
          return new Response(
            JSON.stringify({
              model: "mimo-test-model",
              choices: [
                {
                  finish_reason: "stop",
                  message: {
                    role: "assistant",
                    content: `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

MiMo injected fake fetch 生成结构化 note。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Provider 只能作为 adapter。

## 相关链接

暂无相关链接。
`
                  }
                }
              ],
              usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(requests.length).toBeGreaterThan(0);
    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
  });

  it("records agent quality loop artifacts and repairs weak topic-note relations", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-agent-quality-loop",
      autoApprove: true,
      providerRuntime: {
        provider: "weak-relations-fixture",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    const runRoot = path.join(tempRoot, ".agent-runs/run-agent-quality-loop");
    const loopFiles = await readdir(path.join(runRoot, "agent-loop"));
    const loops = await Promise.all(
      loopFiles.map(
        async (file) =>
          JSON.parse(await readFile(path.join(runRoot, "agent-loop", file), "utf8")) as {
            repairedIssues: string[];
            remainingIssues: string[];
          }
      )
    );
    const loop = loops.find((item) => item.repairedIssues.includes("TOPIC_NOTE_WEAK_RELATIONS"));
    expect(loop).toBeDefined();
    if (!loop) {
      throw new Error("Expected a quality loop artifact with TOPIC_NOTE_WEAK_RELATIONS repair");
    }
    expect(loop.repairedIssues).toContain("TOPIC_NOTE_WEAK_RELATIONS");
    expect(loop.remainingIssues).toEqual([]);
    const validationFiles = await readdir(path.join(runRoot, "validation"));
    const validations = await Promise.all(
      validationFiles
        .filter((file) => file !== "validation.json")
        .map(
          async (file) =>
            JSON.parse(await readFile(path.join(runRoot, "validation", file), "utf8")) as {
              validation: { qualityIssues: string[] };
            }
        )
    );
    expect(validations.flatMap((item) => item.validation.qualityIssues)).not.toContain("TOPIC_NOTE_WEAK_RELATIONS");
    const note = await readFile(path.join(tempRoot, "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"), "utf8");
    expect(note).toContain("## 相关链接");
  });

  it("executes planned note and topic-index work items with artifact-backed eval", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-multi-work-items",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    const runRoot = path.join(tempRoot, ".agent-runs/run-multi-work-items");
    const plan = JSON.parse(await readFile(path.join(runRoot, "plan.json"), "utf8"));
    expect(plan.methodologyId).toBe("lmwiki-v1");
    expect(plan.methodologyVersion).toBe("1");
    expect(plan.workItems.every((item: { methodologyId?: string }) => item.methodologyId === "lmwiki-v1")).toBe(true);
    expect(plan.workItems.filter((item: { type: string }) => item.type === "CREATE_TOPIC_NOTE")).toHaveLength(2);
    expect(plan.workItems.filter((item: { type: string }) => item.type === "REWRITE_TOPIC_NOTE")).toHaveLength(1);
    expect(await fileExists(path.join(runRoot, "patches/create-go-go-基础语法.patch.json"))).toBe(true);
    expect(await fileExists(path.join(runRoot, "patches/maintain-go-index.patch.json"))).toBe(true);
    expect(await fileExists(path.join(runRoot, "patches/maintain-moc.patch.json"))).toBe(true);

    const evalReport = JSON.parse(await readFile(path.join(runRoot, "eval.json"), "utf8"));
    expect(evalReport.methodology).toEqual({ id: "lmwiki-v1", version: "1" });
    expect(evalReport.rawCoverage).toEqual({ total: 3, seen: 3 });
    expect(evalReport.pagesRewritten).toBe(3);
    expect(evalReport.rawMirrorConverted).toBe(1);
    expect(evalReport.agentLoop.total).toBe(plan.workItems.length);
    expect(evalReport.agentLoop.missingArtifacts).toEqual([]);
    expect(evalReport.agentLoop.corruptArtifacts).toEqual([]);
    expect(evalReport.agentLoop.byNode).toEqual({
      note: plan.workItems.filter((item: { type: string }) => item.type === "CREATE_TOPIC_NOTE" || item.type === "REWRITE_TOPIC_NOTE").length,
      "topic-index": plan.workItems.filter((item: { type: string }) => item.type === "MAINTAIN_TOPIC_INDEX").length,
      moc: plan.workItems.filter((item: { type: string }) => item.type === "MAINTAIN_MOC").length,
      "quality-review": plan.workItems.filter((item: { type: string }) => item.type === "QUALITY_REVIEW").length
    });
    expect(evalReport.agentLoop.providerCalls).toBe(3);
    expect(evalReport.agentLoop.budgetExceeded).toEqual([]);
    expect(evalReport.workItemStatuses["maintain-go-index"]).toBe("PUBLISHED");
    expect(evalReport.workItemStatuses["maintain-moc"]).toBe("PUBLISHED");
    expect(evalReport.workItemStatuses["quality-review"]).toBe("SUCCEEDED");

    const report = await readFile(path.join(runRoot, "report.md"), "utf8");
    expect(report).toContain("Pages rewritten: 3");
    expect(report).toContain("Methodology: lmwiki-v1@1");
    const moc = await readFile(path.join(tempRoot, "knowledge-base/moc.md"), "utf8");
    expect(moc).toContain("- [go](topics/go/index.md)");
    expect(moc).toContain("- [tools](topics/tools/index.md)");
  });

  it("reports missing and corrupt agent loop artifacts without changing publish state", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-loop-artifact-audit",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    const runRoot = path.join(tempRoot, ".agent-runs/run-loop-artifact-audit");
    const plan = JSON.parse(await readFile(path.join(runRoot, "plan.json"), "utf8")) as {
      workItems: Array<{ id: string }>;
    };
    const missingId = plan.workItems[0].id;
    const corruptId = plan.workItems[1].id;
    await rm(path.join(runRoot, "agent-loop", `${missingId}.json`));
    await writeFile(path.join(runRoot, "agent-loop", `${corruptId}.json`), "{", "utf8");

    await reportNode({
      runId: "run-loop-artifact-audit",
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      methodologyId: "lmwiki-v1",
      autoApprove: true,
      status: "RUNNING"
    });

    const evalReport = JSON.parse(await readFile(path.join(runRoot, "eval.json"), "utf8"));
    expect(evalReport.agentLoop.missingArtifacts).toContain(missingId);
    expect(evalReport.agentLoop.corruptArtifacts).toContain(`${corruptId}.json`);
    expect(evalReport.workItemStatuses[missingId]).toBe("PUBLISHED");
  });

  it("skips already published work items when rerunning the same run id", async () => {
    await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-work-item-resume",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    const tracePath = path.join(
      tempRoot,
      ".agent-runs/run-work-item-resume/traces/create-go-go-基础语法.jsonl"
    );
    const traceBefore = (await readFile(tracePath, "utf8")).trim().split("\n");

    await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-work-item-resume",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    const traceAfter = (await readFile(tracePath, "utf8")).trim().split("\n");
    expect(traceAfter).toHaveLength(traceBefore.length);
  });

  it("marks published work item as needing replan when target sha changed before rerun", async () => {
    await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-work-item-replan",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    const tracePath = path.join(
      tempRoot,
      ".agent-runs/run-work-item-replan/traces/create-go-go-基础语法.jsonl"
    );
    const traceBefore = (await readFile(tracePath, "utf8")).trim().split("\n");
    const targetPath = path.join(tempRoot, "knowledge-base/topics/go/Go 基础语法.md");
    await writeFile(targetPath, "# User edited Go note\n\n用户手动补充内容。\n", "utf8");

    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-work-item-replan",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("FAILED");
    expect(result.lastError).toBe("WORK_ITEM_NEEDS_REPLAN");
    const traceAfter = (await readFile(tracePath, "utf8")).trim().split("\n");
    expect(traceAfter).toHaveLength(traceBefore.length);
    const workItem = JSON.parse(
      await readFile(path.join(tempRoot, ".agent-runs/run-work-item-replan/work-items/create-go-go-基础语法.json"), "utf8")
    ) as { status: string };
    expect(workItem.status).toBe("NEEDS_REPLAN");
    const report = await readFile(path.join(tempRoot, ".agent-runs/run-work-item-replan/report.md"), "utf8");
    expect(report).toContain("create-go-go-基础语法");
    expect(report).toContain("NEEDS_REPLAN");
    const target = await readFile(targetPath, "utf8");
    expect(target).toContain("用户手动补充内容");
  });
});
