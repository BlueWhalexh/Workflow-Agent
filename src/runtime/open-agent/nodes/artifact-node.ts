import { writeOpenAgentGraphArtifacts } from "../open-agent-artifacts.js";
import type { OpenAgentGraphState } from "../open-agent-state.js";

export async function runArtifactNode(state: OpenAgentGraphState): Promise<OpenAgentGraphState> {
  await writeOpenAgentGraphArtifacts(state);
  state.steps.push({
    name: "ARTIFACT",
    status: "SUCCEEDED",
    summary: `Wrote open agent graph report and trace artifacts.`
  });
  await writeOpenAgentGraphArtifacts(state);
  return state;
}
