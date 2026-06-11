import { Annotation, END, START, StateGraph } from "@langchain/langgraph";
import { executePhaseNode } from "./nodes/execute-phase-node.js";
import { inventoryNode } from "./nodes/inventory-node.js";
import { planNode } from "./nodes/plan-node.js";
import { reportNode } from "./nodes/report-node.js";
import type { RuntimeCheckpointStore } from "./checkpoint-store.js";
import type { GraphState } from "./state.js";

const GraphAnnotation = Annotation.Root({
  runId: Annotation<string>,
  workspaceRoot: Annotation<string>,
  instruction: Annotation<string>,
  autoApprove: Annotation<boolean>,
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
  checkpointStore?: RuntimeCheckpointStore;
}): Promise<GraphState> {
  const graph = new StateGraph(GraphAnnotation)
    .addNode("inventory", inventoryNode)
    .addNode("plan", planNode)
    .addNode("execute", executePhaseNode)
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
      autoApprove: input.autoApprove,
      status: "CREATED"
    },
    {
      configurable: {
        thread_id: input.runId
      }
    }
  );
}
