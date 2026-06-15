import type { OpenAgentGraphState, OpenAgentProvider } from "../open-agent-state.js";
import { ProviderRuntimeError } from "../../../domain/llm-provider/provider-error.js";
import { OpenAgentProviderValidationError } from "../open-agent-provider.js";

export async function runToolLoopNode(state: OpenAgentGraphState, provider: OpenAgentProvider): Promise<OpenAgentGraphState> {
  if (!state.plan) {
    state.status = "FAILED_VALIDATION";
    state.steps.push({
      name: "TOOL_LOOP",
      status: "FAILED",
      summary: "Open agent graph cannot run tool loop without a plan."
    });
    return state;
  }

  let contextReadToolCalls = 0;
  for (let iteration = 1; iteration <= state.loopBudget.maxIterations; iteration += 1) {
    state.loopIterations = iteration;
    if (contextReadToolCalls >= state.loopBudget.maxToolCalls) {
      state.status = "FAILED_BUDGET";
      state.steps.push({
        name: "TOOL_LOOP",
        status: "FAILED",
        summary: "Open agent graph exhausted tool-call budget before solving the task."
      });
      return state;
    }
    let nextAction;
    try {
      nextAction = provider.nextAction
        ? await provider.nextAction({ iteration, plan: state.plan, groundingRefs: state.groundingRefs })
        : { action: "SOLVED" as const, summary: "No provider loop action supplied; treating gathered context as solved." };
      if (provider.nextAction) {
        state.providerCalls += 1;
      }
    } catch (error) {
      if (error instanceof ProviderRuntimeError) {
        state.status = "FAILED_PROVIDER";
      } else if (error instanceof OpenAgentProviderValidationError) {
        state.status = "FAILED_VALIDATION";
      } else {
        state.status = "FAILED_PROVIDER";
      }
      state.steps.push({
        name: "TOOL_LOOP",
        status: "FAILED",
        summary: error instanceof Error ? error.message : String(error)
      });
      return state;
    }

    const event = {
      iteration,
      action: nextAction.action,
      toolName: nextAction.toolName,
      observationRef:
        nextAction.action === "READ_CONTEXT" ? state.groundingRefs[Math.min(iteration - 1, state.groundingRefs.length - 1)] : undefined,
      status: "SUCCEEDED" as const,
      summary: nextAction.summary
    };
    state.traceEvents.push(event);

    if (nextAction.action === "READ_CONTEXT") {
      contextReadToolCalls += 1;
    }

    if (nextAction.action === "REQUEST_WRITE_CONFIRMATION") {
      state.status = "NEEDS_CONFIRMATION";
      state.steps.push({
        name: "TOOL_LOOP",
        status: "SUCCEEDED",
        summary: "Open agent graph stopped for workspace write confirmation."
      });
      return state;
    }

    if (nextAction.action === "SOLVED") {
      state.steps.push({
        name: "TOOL_LOOP",
        status: "SUCCEEDED",
        summary: `Open agent graph solved after ${iteration} iteration(s).`
      });
      return state;
    }
  }

  state.status = "FAILED_BUDGET";
  state.steps.push({
    name: "TOOL_LOOP",
    status: "FAILED",
    summary: "Open agent graph exhausted loop budget before solving the task."
  });
  return state;
}
