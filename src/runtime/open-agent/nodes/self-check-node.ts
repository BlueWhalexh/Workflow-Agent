import type { OpenAgentGraphState } from "../open-agent-state.js";

function synthesizedContent(state: OpenAgentGraphState): string | undefined {
  return state.answer ?? state.draftArtifact?.content ?? state.candidatePatch?.files[0]?.content;
}

export function runSelfCheckNode(state: OpenAgentGraphState): OpenAgentGraphState {
  if ((state.answer || state.draftArtifact || state.candidatePatch) && state.groundingRefs.length === 0) {
    state.status = "FAILED_VALIDATION";
    state.steps.push({
      name: "SELF_CHECK",
      status: "FAILED",
      summary: "Open agent graph output must include at least one grounding ref."
    });
    return state;
  }

  if (state.draftArtifact && !state.draftArtifact.content.includes("Draft only")) {
    state.status = "FAILED_VALIDATION";
    state.steps.push({
      name: "SELF_CHECK",
      status: "FAILED",
      summary: "Draft artifact content must include the Draft only marker."
    });
    return state;
  }

  if (state.synthesis?.groundingRefs.some((ref) => !state.groundingRefs.includes(ref))) {
    state.status = "FAILED_VALIDATION";
    state.steps.push({
      name: "SELF_CHECK",
      status: "FAILED",
      summary: "Provider synthesis grounding refs must come from gathered context."
    });
    return state;
  }

  const content = synthesizedContent(state);
  if (state.synthesis && content && state.synthesis.groundingRefs.some((ref) => !content.includes(ref))) {
    state.status = "FAILED_VALIDATION";
    state.steps.push({
      name: "SELF_CHECK",
      status: "FAILED",
      summary: "Provider synthesis content must include its grounding refs."
    });
    return state;
  }

  if (state.candidatePatch?.targetPaths.some((targetPath) => !targetPath.startsWith("knowledge-base/"))) {
    state.status = "FAILED_POLICY";
    state.steps.push({
      name: "SELF_CHECK",
      status: "FAILED",
      summary: "Candidate patch targets must stay under knowledge-base/."
    });
    return state;
  }

  state.steps.push({
    name: "SELF_CHECK",
    status: "SUCCEEDED",
    summary: "Verified open agent graph output grounding and write boundary."
  });
  return state;
}
