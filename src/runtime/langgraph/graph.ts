import { Annotation, END, START, StateGraph } from "@langchain/langgraph";
import { executePhaseNode } from "./nodes/execute-phase-node.js";
import { inventoryNode } from "./nodes/inventory-node.js";
import { planNode } from "./nodes/plan-node.js";
import { reportNode } from "./nodes/report-node.js";
import type { GraphState } from "./state.js";

const GraphAnnotation = Annotation.Root({
  runId: Annotation<string>,
  workspaceRoot: Annotation<string>,
  instruction: Annotation<string>,
  autoApprove: Annotation<boolean>,
  status: Annotation<GraphState["status"]>,
  planPath: Annotation<string | undefined>,
  reportPath: Annotation<string | undefined>,
  lastError: Annotation<string | undefined>
});

export async function runOrganizeWorkflow(input: {
  workspaceRoot: string;
  instruction: string;
  runId: string;
  autoApprove: boolean;
}): Promise<GraphState> {
  const graph = new StateGraph(GraphAnnotation)
    .addNode("inventory", inventoryNode)
    .addNode("plan", planNode)
    .addNode("execute", executePhaseNode)
    .addNode("report", reportNode)
    .addEdge(START, "inventory")
    .addEdge("inventory", "plan")
    .addEdge("plan", "execute")
    .addEdge("execute", "report")
    .addEdge("report", END)
    .compile();

  return graph.invoke({
    runId: input.runId,
    workspaceRoot: input.workspaceRoot,
    instruction: input.instruction,
    autoApprove: input.autoApprove,
    status: "CREATED"
  });
}
