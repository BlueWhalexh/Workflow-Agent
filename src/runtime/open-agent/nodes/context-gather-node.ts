import { readWorkspaceFile } from "../../../storage/workspace-fs.js";
import { scanWorkspace } from "../../../domain/workspace/inventory.js";
import type { OpenAgentGraphState } from "../open-agent-state.js";

function pickGroundingRefs(paths: string[], hints: string[]): string[] {
  const hinted = paths.filter((filePath) => hints.some((hint) => filePath.includes(hint)));
  return [...hinted, ...paths].filter((value, index, array) => array.indexOf(value) === index).slice(0, 5);
}

export async function runContextGatherNode(state: OpenAgentGraphState): Promise<OpenAgentGraphState> {
  const inventory = await scanWorkspace({ workspaceRoot: state.workspaceRoot });
  state.rawFiles = inventory.rawFiles.map((file) => file.path).sort();
  state.knowledgePages = inventory.knowledgeBasePages.map((page) => page.path).sort();
  state.groundingRefs = pickGroundingRefs([...state.rawFiles, ...state.knowledgePages], state.plan?.contextHints ?? []);

  state.contextDigest = await Promise.all(
    state.groundingRefs.slice(0, 3).map(async (ref) => {
      const content = await readWorkspaceFile(state.workspaceRoot, ref);
      return {
        path: ref,
        excerpt: content.slice(0, 800)
      };
    })
  );

  state.toolCalls.push({
    name: "workspace.scan",
    risk: "READ_ONLY",
    status: "SUCCEEDED",
    summary: `Scanned ${state.rawFiles.length} raw files and ${state.knowledgePages.length} knowledge pages.`,
    refs: state.groundingRefs
  });
  state.steps.push({
    name: "CONTEXT_GATHER",
    status: "SUCCEEDED",
    summary: `Selected ${state.groundingRefs.length} grounding refs.`
  });
  return state;
}
