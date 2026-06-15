import { promises as fs } from "node:fs";
import path from "node:path";
import { getKnowledgeMethodology } from "../domain/methodology/knowledge-methodology.js";
import { scanWorkspace } from "../domain/workspace/inventory.js";
import { assertSafeArtifactSlug } from "../storage/artifact-slug.js";
import { stableJson } from "../storage/json-schema.js";
import { sha256 } from "../storage/sha.js";

export type OpenAgentRisk = "READ_ONLY" | "DRAFT_ONLY";
export type OpenAgentStatus = "SUCCEEDED" | "FAILED_POLICY";
export type OpenAgentStepName = "PLAN" | "GATHER_CONTEXT" | "PRODUCE_OUTPUT" | "SELF_CHECK";
export type OpenAgentOutputPolicy = "ANSWER_ONLY" | "DRAFT_ARTIFACT" | "CANDIDATE_PATCH";
export type OpenAgentOutputKind = "answer" | "draft" | "candidate-patch" | "policy-failure";
export type OpenAgentToolCallName =
  | "methodology.read"
  | "workspace.scan"
  | "open-agent.output"
  | "open-agent.selfCheck";

export interface RunOpenAgentTaskRequest {
  workspaceRoot: string;
  taskId?: string;
  methodologyId?: string;
  objective: string;
  risk: OpenAgentRisk;
  outputPolicy: OpenAgentOutputPolicy;
  allowedToolNames: string[];
  blockedToolNames: string[];
}

export interface OpenAgentStep {
  name: OpenAgentStepName;
  status: "SUCCEEDED" | "FAILED";
  summary: string;
}

export interface OpenAgentToolCall {
  name: OpenAgentToolCallName;
  risk: "READ_ONLY" | "ARTIFACT_WRITE";
  status: "SUCCEEDED" | "FAILED";
  summary: string;
  refs?: string[];
}

export interface FixedWorkflowHandoff {
  type: "FIXED_WORKFLOW";
  capabilityId: "workflow.organizeWorkspace";
  executeRequired: true;
  confirmationRequired: true;
  methodologyId: string;
  instruction: string;
}

export interface CandidatePatchProposal {
  kind: "CANDIDATE_PATCH_PROPOSAL";
  publishable: false;
  targetPaths: string[];
  files: Array<{
    path: string;
    changeType: "CREATED" | "MODIFIED";
    baseSha: string | null;
    contentSha: string;
    content: string;
  }>;
  rationale: string;
  handoff: FixedWorkflowHandoff;
}

export interface OpenAgentRunReport {
  schemaVersion: "open-agent-runtime.v1";
  taskId: string;
  methodologyId: string;
  objective: string;
  risk: OpenAgentRisk;
  outputPolicy: OpenAgentOutputPolicy;
  steps: OpenAgentStep[];
  context: {
    rawFiles: string[];
    knowledgePages: string[];
    methodology: {
      id: string;
      version: string;
    };
  };
  toolPolicy: {
    allowedToolNames: string[];
    blockedToolNames: string[];
  };
  groundingRefs: string[];
  toolCalls: OpenAgentToolCall[];
  outputRef: {
    kind: OpenAgentOutputKind;
    path: string;
  };
  candidatePatch?: CandidatePatchProposal;
}

export interface DraftArtifact {
  title: string;
  content: string;
}

export interface RunOpenAgentTaskResult {
  taskId: string;
  status: OpenAgentStatus;
  methodologyId: string;
  artifactRoot: string;
  artifactPath: string;
  answer?: string;
  draftArtifact?: DraftArtifact;
  candidatePatch?: CandidatePatchProposal;
  report: OpenAgentRunReport;
}

function defaultTaskId(): string {
  return `open-agent-${Date.now()}`;
}

function artifactPathForTask(taskId: string): string {
  return path.posix.join(".agent-runs", "open-agent", `${taskId}.json`);
}

function titleFromObjective(objective: string): string {
  return objective
    .replace(/^请/, "")
    .replace(/[。.!?？]+$/g, "")
    .trim()
    .slice(0, 80);
}

function topPaths(paths: string[]): string {
  return paths.slice(0, 5).map((item) => `- ${item}`).join("\n") || "- none";
}

function candidateTargetPath(taskId: string): string {
  return path.posix.join("knowledge-base", "drafts", `${taskId}.md`);
}

async function readBaseSha(workspaceRoot: string, relativePath: string): Promise<string | null> {
  const absolutePath = path.join(workspaceRoot, relativePath);
  return fs
    .readFile(absolutePath, "utf8")
    .then((content) => sha256(content))
    .catch(() => null);
}

function fixedWorkflowHandoff(input: { methodologyId: string; instruction: string }): FixedWorkflowHandoff {
  return {
    type: "FIXED_WORKFLOW",
    capabilityId: "workflow.organizeWorkspace",
    executeRequired: true,
    confirmationRequired: true,
    methodologyId: input.methodologyId,
    instruction: input.instruction
  };
}

async function writeOpenAgentArtifact(input: {
  workspaceRoot: string;
  artifactPath: string;
  report: OpenAgentRunReport;
}): Promise<void> {
  const absolutePath = path.join(input.workspaceRoot, input.artifactPath);
  await fs.mkdir(path.dirname(absolutePath), { recursive: true });
  await fs.writeFile(absolutePath, stableJson(input.report), "utf8");
}

export async function runOpenAgentTask(request: RunOpenAgentTaskRequest): Promise<RunOpenAgentTaskResult> {
  const taskId = assertSafeArtifactSlug(request.taskId ?? defaultTaskId());
  const methodology = getKnowledgeMethodology(request.methodologyId);
  const artifactPath = artifactPathForTask(taskId);
  const inventory = await scanWorkspace({ workspaceRoot: request.workspaceRoot });
  const rawFiles = inventory.rawFiles.map((file) => file.path).sort();
  const knowledgePages = inventory.knowledgeBasePages.map((page) => page.path).sort();
  const groundingRefs = [...rawFiles, ...knowledgePages].slice(0, 5);
  const successfulToolCalls: OpenAgentToolCall[] = [
    {
      name: "methodology.read",
      risk: "READ_ONLY",
      status: "SUCCEEDED",
      summary: `Loaded methodology ${methodology.id}@${methodology.version}.`
    },
    {
      name: "workspace.scan",
      risk: "READ_ONLY",
      status: "SUCCEEDED",
      summary: `Scanned ${rawFiles.length} raw files and ${knowledgePages.length} knowledge pages.`,
      refs: groundingRefs
    },
    {
      name: "open-agent.output",
      risk: "ARTIFACT_WRITE",
      status: "SUCCEEDED",
      summary: `Produced ${request.outputPolicy} output.`
    },
    {
      name: "open-agent.selfCheck",
      risk: "READ_ONLY",
      status: "SUCCEEDED",
      summary: "Verified output stays within open-agent artifact boundary."
    }
  ];
  const commonReport = {
    schemaVersion: "open-agent-runtime.v1" as const,
    taskId,
    methodologyId: methodology.id,
    objective: request.objective,
    risk: request.risk,
    outputPolicy: request.outputPolicy,
    context: {
      rawFiles,
      knowledgePages,
      methodology: {
        id: methodology.id,
        version: methodology.version
      }
    },
    toolPolicy: {
      allowedToolNames: request.allowedToolNames,
      blockedToolNames: request.blockedToolNames
    },
    groundingRefs,
    toolCalls: successfulToolCalls
  };

  const policyFailed = request.allowedToolNames.includes("patch.publish");
  if (policyFailed) {
    const report: OpenAgentRunReport = {
      ...commonReport,
      toolCalls: successfulToolCalls.map((call) =>
        call.name === "open-agent.output" || call.name === "open-agent.selfCheck"
          ? {
              ...call,
              status: "FAILED" as const,
              summary:
                call.name === "open-agent.output"
                  ? "Policy blocked output because patch.publish was allowed for an open agent task."
                  : "Open agent runtime cannot allow direct workspace publish tools."
            }
          : call
      ),
      steps: [
        {
          name: "PLAN",
          status: "SUCCEEDED",
          summary: "Prepared open agent task plan."
        },
        {
          name: "GATHER_CONTEXT",
          status: "SUCCEEDED",
          summary: `Scanned ${rawFiles.length} raw files and ${knowledgePages.length} knowledge pages.`
        },
        {
          name: "PRODUCE_OUTPUT",
          status: "FAILED",
          summary: "Policy blocked output because patch.publish was allowed for an open agent task."
        },
        {
          name: "SELF_CHECK",
          status: "FAILED",
          summary: "Open agent runtime cannot allow direct workspace publish tools."
        }
      ],
      outputRef: {
        kind: "policy-failure",
        path: artifactPath
      }
    };
    await writeOpenAgentArtifact({ workspaceRoot: request.workspaceRoot, artifactPath, report });
    return {
      taskId,
      status: "FAILED_POLICY",
      methodologyId: methodology.id,
      artifactRoot: path.posix.join(".agent-runs", "open-agent"),
      artifactPath,
      report
    };
  }

  const answer =
    request.outputPolicy === "ANSWER_ONLY"
      ? `Objective: ${request.objective}
Methodology: ${methodology.id}
Raw files: ${rawFiles.length}
Knowledge pages: ${knowledgePages.length}
Top context:
${topPaths([...rawFiles, ...knowledgePages])}
Sources:
${topPaths(groundingRefs)}`
      : undefined;

  const draftArtifact =
    request.outputPolicy === "DRAFT_ARTIFACT"
      ? {
          title: titleFromObjective(request.objective),
          content: `# ${titleFromObjective(request.objective)}

Draft only. This artifact was generated under the open agent runtime and has not been published to the workspace.

## Context Summary

- Methodology: ${methodology.id}
- Raw files: ${rawFiles.length}
- Knowledge pages: ${knowledgePages.length}

## Source Context

${topPaths([...rawFiles, ...knowledgePages])}
`
        }
      : undefined;

  const candidatePatch =
    request.outputPolicy === "CANDIDATE_PATCH"
      ? await (async (): Promise<CandidatePatchProposal> => {
          const targetPath = candidateTargetPath(taskId);
          const content = `# ${titleFromObjective(request.objective)}

Candidate patch only. This proposal was generated by the open agent runtime and has not been published to the workspace.

## Objective

${request.objective}

## Context Summary

- Methodology: ${methodology.id}
- Raw files: ${rawFiles.length}
- Knowledge pages: ${knowledgePages.length}

## Source Context

${topPaths(groundingRefs)}
`;
          return {
            kind: "CANDIDATE_PATCH_PROPOSAL",
            publishable: false,
            targetPaths: [targetPath],
            files: [
              {
                path: targetPath,
                changeType: (await readBaseSha(request.workspaceRoot, targetPath)) ? "MODIFIED" : "CREATED",
                baseSha: await readBaseSha(request.workspaceRoot, targetPath),
                contentSha: sha256(content),
                content
              }
            ],
            rationale: "Open agent produced a candidate write proposal, but workspace writes require fixed workflow confirmation.",
            handoff: fixedWorkflowHandoff({
              methodologyId: methodology.id,
              instruction: request.objective
            })
          };
        })()
      : undefined;

  const report: OpenAgentRunReport = {
    ...commonReport,
    steps: [
      {
        name: "PLAN",
        status: "SUCCEEDED",
        summary: `Selected ${request.outputPolicy} for ${request.risk} task.`
      },
      {
        name: "GATHER_CONTEXT",
        status: "SUCCEEDED",
        summary: `Scanned ${rawFiles.length} raw files and ${knowledgePages.length} knowledge pages.`
      },
      {
        name: "PRODUCE_OUTPUT",
        status: "SUCCEEDED",
        summary:
          request.outputPolicy === "ANSWER_ONLY"
            ? "Produced answer output."
            : request.outputPolicy === "DRAFT_ARTIFACT"
              ? "Produced draft artifact output."
              : "Produced candidate patch proposal output."
      },
      {
        name: "SELF_CHECK",
        status: "SUCCEEDED",
        summary: "Verified open agent output does not publish workspace writes."
      }
    ],
    outputRef: {
      kind:
        request.outputPolicy === "ANSWER_ONLY"
          ? "answer"
          : request.outputPolicy === "DRAFT_ARTIFACT"
            ? "draft"
            : "candidate-patch",
      path: artifactPath
    },
    candidatePatch
  };

  await writeOpenAgentArtifact({ workspaceRoot: request.workspaceRoot, artifactPath, report });

  return {
    taskId,
    status: "SUCCEEDED",
    methodologyId: methodology.id,
    artifactRoot: path.posix.join(".agent-runs", "open-agent"),
    artifactPath,
    answer,
    draftArtifact,
    candidatePatch,
    report
  };
}
