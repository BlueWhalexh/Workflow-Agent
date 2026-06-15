import { getKnowledgeMethodology } from "../../../domain/methodology/knowledge-methodology.js";
import type { OpenAgentGraphState } from "../open-agent-state.js";

export function runPolicyGateNode(state: OpenAgentGraphState): OpenAgentGraphState {
  try {
    const methodology = getKnowledgeMethodology(state.methodologyId);
    state.methodologyId = methodology.id;
  } catch (error) {
    state.status = "FAILED_POLICY";
    state.steps.push({
      name: "POLICY_GATE",
      status: "FAILED",
      summary: error instanceof Error ? error.message : String(error)
    });
    return state;
  }

  if (state.allowedToolNames.includes("patch.publish")) {
    state.status = "FAILED_POLICY";
    state.steps.push({
      name: "POLICY_GATE",
      status: "FAILED",
      summary: "Open agent graph cannot allow patch.publish."
    });
    return state;
  }

  if (state.loopBudget.maxIterations <= 0 || state.loopBudget.maxToolCalls <= 0) {
    state.status = "FAILED_BUDGET";
    state.steps.push({
      name: "POLICY_GATE",
      status: "FAILED",
      summary: "Open agent graph loop budget must allow at least one iteration and one tool call."
    });
    return state;
  }

  state.steps.push({
    name: "POLICY_GATE",
    status: "SUCCEEDED",
    summary: "Open agent graph policy gate passed."
  });
  return state;
}
