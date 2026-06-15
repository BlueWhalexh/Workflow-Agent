import { getKnowledgeMethodology } from "../../../domain/methodology/knowledge-methodology.js";
import { ProviderRuntimeError } from "../../../domain/llm-provider/provider-error.js";
import { OpenAgentProviderValidationError, parseOpenAgentPlan } from "../open-agent-provider.js";
import type { OpenAgentGraphState, OpenAgentProvider } from "../open-agent-state.js";

export async function runPlanNode(state: OpenAgentGraphState, provider: OpenAgentProvider): Promise<OpenAgentGraphState> {
  try {
    const plan = parseOpenAgentPlan(await provider.plan({ objective: state.message, outputPolicy: state.outputPolicy }));
    state.providerCalls += 1;
    state.plan = plan;
    const methodology = getKnowledgeMethodology(state.methodologyId);
    state.toolCalls.push({
      name: "methodology.read",
      risk: "READ_ONLY",
      status: "SUCCEEDED",
      summary: `Loaded methodology ${methodology.id}@${methodology.version}.`
    });
    state.steps.push({
      name: "PLAN",
      status: "SUCCEEDED",
      summary: `Open agent graph planned ${plan.steps.length} steps.`
    });
  } catch (error) {
    state.providerCalls += error instanceof OpenAgentProviderValidationError || error instanceof ProviderRuntimeError ? 1 : 0;
    state.status = error instanceof ProviderRuntimeError ? "FAILED_PROVIDER" : "FAILED_VALIDATION";
    state.steps.push({
      name: "PLAN",
      status: "FAILED",
      summary: error instanceof Error ? error.message : String(error)
    });
  }
  return state;
}
