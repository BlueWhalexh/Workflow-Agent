import { Annotation, END, START, StateGraph } from "@langchain/langgraph";
import { executePhaseNode } from "./nodes/execute-phase-node.js";
import { inventoryNode } from "./nodes/inventory-node.js";
import { planNode } from "./nodes/plan-node.js";
import { reportNode } from "./nodes/report-node.js";
import type { RuntimeCheckpointStore } from "./checkpoint-store.js";
import type { ProviderRuntimeDependencies } from "../provider/provider-registry.js";
import type { ProviderRuntimeConfig } from "../provider/provider-runtime-config.js";
import type { GraphState } from "./state.js";
import { getKnowledgeMethodology } from "../../domain/methodology/knowledge-methodology.js";

const GraphAnnotation = Annotation.Root({
  runId: Annotation<string>,
  workspaceRoot: Annotation<string>,
  instruction: Annotation<string>,
  methodologyId: Annotation<string>,
  autoApprove: Annotation<boolean>,
  providerRuntime: Annotation<GraphState["providerRuntime"]>,
  status: Annotation<GraphState["status"]>,
  planPath: Annotation<string | undefined>,
  reportPath: Annotation<string | undefined>,
  pendingApproval: Annotation<GraphState["pendingApproval"]>,
  lastError: Annotation<string | undefined>
});

export async function runOrganizeWorkflow(input: {
  workspaceRoot: string;
  instruction: string;
  runId: string;
  autoApprove: boolean;
  methodologyId?: string;
  checkpointStore?: RuntimeCheckpointStore;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}): Promise<GraphState> {
  const methodology = getKnowledgeMethodology(input.methodologyId);
  const graph = new StateGraph(GraphAnnotation)
    .addNode("inventory", inventoryNode)
    .addNode("plan", planNode)
    .addNode("execute", (state) => executePhaseNode(state, input.providerRuntimeDependencies))
    .addNode("report", reportNode)
    .addEdge(START, "inventory")
    .addEdge("inventory", "plan")
    .addConditionalEdges("plan", (state) => {
      return state.status === "WAITING_PLAN_APPROVAL" ? "report" : "execute";
    })
    .addEdge("execute", "report")
    .addEdge("report", END);

  const compiled = graph.compile({
    checkpointer: input.checkpointStore?.checkpointer
  });

  return compiled.invoke(
    {
      runId: input.runId,
      workspaceRoot: input.workspaceRoot,
      instruction: input.instruction,
      methodologyId: methodology.id,
      autoApprove: input.autoApprove,
      providerRuntime: input.providerRuntime,
      status: "CREATED"
    },
    {
      configurable: {
        thread_id: input.runId
      }
    }
  );
}
