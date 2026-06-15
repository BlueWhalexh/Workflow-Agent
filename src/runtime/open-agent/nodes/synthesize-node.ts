import path from "node:path";
import { promises as fs } from "node:fs";
import { sha256 } from "../../../storage/sha.js";
import type { CandidatePatchProposal, FixedWorkflowHandoff } from "../../../sdk/open-agent-runtime.js";
import { ProviderRuntimeError } from "../../../domain/llm-provider/provider-error.js";
import { OpenAgentProviderValidationError, parseOpenAgentSynthesisOutput } from "../open-agent-provider.js";
import type { OpenAgentGraphState, OpenAgentProvider, OpenAgentSynthesisOutput } from "../open-agent-state.js";

function titleFromObjective(objective: string): string {
  return objective
    .replace(/^请/, "")
    .replace(/[。.!?？]+$/g, "")
    .trim()
    .slice(0, 80);
}

function topPaths(paths: string[]): string {
  return paths.map((item) => `- ${item}`).join("\n") || "- none";
}

function materializeGroundingSources(
  content: string,
  groundingRefs: string[],
  input: { markdownHeading?: boolean } = {}
): string {
  const missingRefs = groundingRefs.filter((ref) => !content.includes(ref));
  if (missingRefs.length === 0) {
    return content;
  }
  const heading = input.markdownHeading ? "## Sources" : "Sources:";
  return `${content.trimEnd()}

${heading}
${topPaths(missingRefs)}`;
}

async function readBaseSha(workspaceRoot: string, relativePath: string): Promise<string | null> {
  return fs
    .readFile(path.join(workspaceRoot, relativePath), "utf8")
    .then((content) => sha256(content))
    .catch(() => null);
}

function handoff(state: OpenAgentGraphState): FixedWorkflowHandoff {
  return {
    type: "FIXED_WORKFLOW",
    capabilityId: "workflow.organizeWorkspace",
    executeRequired: true,
    confirmationRequired: true,
    methodologyId: state.methodologyId,
    instruction: state.message
  };
}

function deterministicTargetPath(state: OpenAgentGraphState): string {
  return path.posix.join("knowledge-base", "drafts", `${state.taskId}.md`);
}

async function candidatePatchFromContent(state: OpenAgentGraphState, content: string): Promise<CandidatePatchProposal> {
  const targetPath = deterministicTargetPath(state);
  const baseSha = await readBaseSha(state.workspaceRoot, targetPath);
  return {
    kind: "CANDIDATE_PATCH_PROPOSAL",
    publishable: false,
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: baseSha ? "MODIFIED" : "CREATED",
        baseSha,
        contentSha: sha256(content),
        content
      }
    ],
    rationale: "Open agent graph produced a candidate write proposal; fixed workflow confirmation is required before any publish.",
    handoff: handoff(state)
  };
}

function outputKindForPolicy(state: OpenAgentGraphState): OpenAgentSynthesisOutput["kind"] {
  if (state.outputPolicy === "ANSWER_ONLY") {
    return "ANSWER";
  }
  return state.outputPolicy;
}

function synthesisKindMatchesPolicy(state: OpenAgentGraphState, output: OpenAgentSynthesisOutput): boolean {
  return output.kind === outputKindForPolicy(state);
}

async function applyProviderSynthesis(
  state: OpenAgentGraphState,
  output: OpenAgentSynthesisOutput,
  providerCallId?: string
): Promise<void> {
  if (!synthesisKindMatchesPolicy(state, output)) {
    throw new OpenAgentProviderValidationError("Open agent synthesis kind does not match requested output policy.");
  }
  if (output.kind === "ANSWER") {
    state.answer = materializeGroundingSources(output.answer, output.groundingRefs);
  }
  if (output.kind === "DRAFT_ARTIFACT") {
    state.draftArtifact = {
      title: output.title,
      content: materializeGroundingSources(output.content, output.groundingRefs, { markdownHeading: true })
    };
  }
  if (output.kind === "CANDIDATE_PATCH") {
    if (!output.targetPath.startsWith("knowledge-base/")) {
      state.status = "FAILED_POLICY";
      state.steps.push({
        name: "SYNTHESIZE",
        status: "FAILED",
        summary: "Provider candidate patch target must stay under knowledge-base/."
      });
      return;
    }
    state.candidatePatch = await candidatePatchFromContent(
      state,
      materializeGroundingSources(output.content, output.groundingRefs, { markdownHeading: true })
    );
  }
  state.synthesis = {
    providerBacked: true,
    providerCallId,
    outputKind: output.kind,
    groundingRefs: output.groundingRefs
  };
}

export async function runSynthesizeNode(state: OpenAgentGraphState, provider?: OpenAgentProvider): Promise<OpenAgentGraphState> {
  if (state.status === "NEEDS_CONFIRMATION") {
    state.confirmation = {
      required: true,
      questions: ["请确认要写入的对象、范围和目标 methodology。"],
      handoff: handoff(state)
    };
    state.steps.push({
      name: "HANDOFF",
      status: "SUCCEEDED",
      summary: "Generated fixed workflow handoff for confirmation."
    });
    return state;
  }

  if (provider?.synthesize) {
    try {
      const rawProviderRefCount = state.rawProviderRefs.length;
      const output = parseOpenAgentSynthesisOutput(
        await provider.synthesize({
          objective: state.message,
          outputPolicy: state.outputPolicy,
          methodologyId: state.methodologyId,
          groundingRefs: state.groundingRefs,
          contextDigest: state.contextDigest
        })
      );
      state.providerCalls += 1;
      const providerCallId = state.rawProviderRefs[rawProviderRefCount]?.providerCallId;
      await applyProviderSynthesis(state, output, providerCallId);
      if (state.status === "FAILED_POLICY") {
        return state;
      }
      state.steps.push({
        name: "SYNTHESIZE",
        status: "SUCCEEDED",
        summary: `Synthesized ${state.outputPolicy} output with provider.`
      });
      return state;
    } catch (error) {
      state.providerCalls += error instanceof OpenAgentProviderValidationError || error instanceof ProviderRuntimeError ? 1 : 0;
      state.status = error instanceof ProviderRuntimeError ? "FAILED_PROVIDER" : "FAILED_VALIDATION";
      state.steps.push({
        name: "SYNTHESIZE",
        status: "FAILED",
        summary: error instanceof Error ? error.message : String(error)
      });
      return state;
    }
  }

  if (state.outputPolicy === "ANSWER_ONLY") {
    state.answer = `Objective: ${state.message}
Methodology: ${state.methodologyId}
Loop iterations: ${state.loopIterations}
Sources:
${topPaths(state.groundingRefs)}`;
  }

  if (state.outputPolicy === "DRAFT_ARTIFACT") {
    state.draftArtifact = {
      title: titleFromObjective(state.message),
      content: `# ${titleFromObjective(state.message)}

Draft only. This artifact was generated by the LLM-backed open agent graph and has not been published to the workspace.

## Sources

${topPaths(state.groundingRefs)}
`
    };
  }

  if (state.outputPolicy === "CANDIDATE_PATCH") {
    const content = `# ${titleFromObjective(state.message)}

Candidate patch only. This proposal was generated by the LLM-backed open agent graph and has not been published to the workspace.

## Objective

${state.message}

## Sources

${topPaths(state.groundingRefs)}
`;
    state.candidatePatch = await candidatePatchFromContent(state, content);
  }

  state.synthesis = {
    providerBacked: false,
    outputKind: outputKindForPolicy(state),
    groundingRefs: state.groundingRefs
  };

  state.steps.push({
    name: "SYNTHESIZE",
    status: "SUCCEEDED",
    summary: `Synthesized ${state.outputPolicy} output.`
  });
  return state;
}
