# Real Provider Quality Harness Spec

> 状态：Phase 23 candidate spec。目标是把一次性的 MiMo real workflow smoke 升级为可重复、可审计、secret-safe 的真实 provider 质量验收框架。本文不记录任何 API key/token 值。

## 1. Current Assessment

当前版本符合 **Phase 22 真实工作流 smoke** 的预期：

- `mimo-real-smoke` 已证明真实 `/chat/completions` 可调用。
- `mimo-v2.5` 已通过真实 `/v1/models` 和 workflow smoke 验证。
- `mimo-real organize workflow smoke` 已在临时 fixture workspace 上完整通过：
  - `providerCalls = 3`
  - `agentLoop reports = 8/8`
  - `missingArtifacts = []`
  - `corruptArtifacts = []`
  - `budgetExceeded = []`
  - all note/index/MOC work items published
  - quality review succeeded
- 真实输出暴露的问题已进入 deterministic quality loop：
  - 中英文 heading 同义归一化；
  - related links 占位清理；
  - 不靠 prompt 兜底。

但当前版本还不等于生产可放开：

- 真实 workflow smoke 是人工命令，不是可复用 CLI harness。
- 真实验收输出没有标准 JSON contract。
- 没有 provider/model promotion gate。
- 没有区分 “real smoke passed once” 和 “provider 可以用于真实 workspace”。
- 没有稳定的 drift regression corpus。
- 没有对真实输出的质量修复分布做阈值治理。

## 2. Problem Scenario

场景：开发者接入一个新 provider 或升级模型版本，例如 `mimo-v2.5-pro`、DeepSeek 新模型、未来 Claude Code SDK。

如果只跑一次手写命令：

- 容易漏掉 token 泄露风险；
- 输出字段、模型名、错误体、语言风格和 markdown 结构漂移难复现；
- 失败时很难判断是 provider、prompt、quality loop、validator、merge 还是 report 问题；
- 下一个 agent 无法基于同一标准判断“这个 provider 是否可升级”。

如果做 Phase 23：

- 每个真实 provider 有统一 smoke manifest；
- 每次真实 workflow smoke 都产出同一 JSON summary；
- secret handling 固化为 stdin/env-only；
- pass/fail gate 可以阻断误升级；
- deterministic repairs 可以被统计，而不是隐藏在正文里；
- 模型升级有清晰 promote / reject 依据。

## 3. Goal

建设 **Real Provider Quality Harness**：

```text
provider/model config
  -> model discovery / preflight
  -> disposable workspace workflow smoke
  -> redacted real-smoke result summary
  -> provider quality gate
  -> promotion decision artifact
```

验收目标：

- 真实 provider workflow smoke 可通过单一 CLI 执行。
- 输出 `real-provider-smoke.v1` JSON summary。
- 默认 automated test suite 仍不执行真实外部调用。
- 所有真实调用必须显式 opt-in。
- API key/token 不出现在 argv、docs、tests、artifacts、stdout/stderr。
- 对 provider/model 的 promotion 有确定性标准。

## 4. Non-Goals

本阶段不做：

- 不把 `mimo-real` 设为默认 provider。
- 不在 CI 默认执行真实网络调用。
- 不对用户真实 workspace 自动发布。
- 不实现 rate limit queue、fallback、成本控制或多租户配额。
- 不接前端。
- 不引入数据库。
- 不记录 raw provider payload，除非用户单独启用 redacted raw capture。

## 5. Smoke Result Contract

新增 summary artifact / stdout contract：

```ts
interface RealProviderWorkflowSmokeSummary {
  schemaVersion: "real-provider-workflow-smoke.v1";
  provider: "mimo-real" | "deepseek-real";
  model: string;
  baseUrl: string;
  realExternalCall: true;
  workspaceMode: "TEMP_FIXTURE";
  status: "PASSED" | "FAILED";
  failureStage?: "MODEL_DISCOVERY" | "PROVIDER" | "LOOP" | "VALIDATOR" | "MERGE" | "PUBLISH" | "REPORT";
  runId: string;
  artifactRoot: string;
  eval?: {
    rawCoverage: { total: number; seen: number };
    pagesRewritten: number;
    rawMirrorConverted: number;
    agentLoop: {
      total: number;
      reports: number;
      providerCalls: number;
      missingArtifacts: string[];
      corruptArtifacts: string[];
      budgetExceeded: string[];
      repairedIssues: string[];
      remainingIssues: string[];
    };
  };
  qualityGate: {
    allowed: boolean;
    blockers: string[];
    warnings: string[];
    repairCounts: Record<string, number>;
  };
}
```

## 6. Promotion Gate

Provider/model 可以进入 “可用于真实 workspace 的候选” 前，必须满足：

- `status === "PASSED"`。
- `eval.rawCoverage.seen === eval.rawCoverage.total`。
- `eval.pagesRewritten >= 1`。
- `eval.agentLoop.providerCalls > 0`。
- `eval.agentLoop.missingArtifacts.length === 0`。
- `eval.agentLoop.corruptArtifacts.length === 0`。
- `eval.agentLoop.budgetExceeded.length === 0`。
- 所有 note/index/MOC work items published。
- `remainingIssues.length === 0`。
- deterministic repairs 只允许出现在 allow-list：
  - `TOPIC_NOTE_WEAK_RELATIONS`
  - `TOPIC_NOTE_NON_CANONICAL_HEADINGS`
  - `TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS`
- 不允许出现 placeholder blocker、raw mirror blocker、schema write blocker。

第一版 gate 只给出 `allowed`，不自动修改 provider 默认策略。

## 7. Implementation Shape

新增 CLI：

```bash
node --import tsx src/cli/real-workflow-smoke.ts \
  --provider mimo-real \
  --execute-real \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5
```

行为：

- 复制 `tests/fixtures/workspaces/basic-raw-mirror` 到临时目录。
- 调用 `runOrganizeWorkflow`。
- 读取 `eval.json`、`work-items/*.json`、`validation/*.json`、`agent-loop/*.json`。
- 生成 `RealProviderWorkflowSmokeSummary`。
- stdout 只输出 summary，不输出 token，不输出完整 provider response。

测试策略：

- unit tests 用 injected fake fetch，不真实联网。
- integration tests 跑 fake/fake-fetch workflow，不真实联网。
- 真实调用作为手动 opt-in 验收命令，结果记录到 delivery report。

## 8. Upgrade Conditions

完成 Phase 23 后，才允许讨论：

- 真实 provider 用于非临时 workspace 的 candidate patch-only run。
- 多 provider 对比评分。
- provider fallback。
- rate limit / timeout / retry policy。
- durable checkpoint saver。

## 9. Review Focus

- CLI 是否完全 secret-safe。
- summary contract 是否足够定位失败阶段。
- quality gate 是否避免把真实模型的格式漂移当成成功。
- promotion gate 是否不会误把一次 smoke pass 当成默认生产可用。
- 默认 test suite 是否仍无网络、无 token、可稳定通过。
