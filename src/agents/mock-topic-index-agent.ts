import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import { sha256 } from "../storage/sha.js";
import type { WorkItemAgentInput } from "./work-item-agent.js";

export async function runMockTopicIndexAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const content = `# Topic Index

- [[Skill vs CLI Tool 决策]]
`;

  return {
    workItemId: input.workItem.id,
    status: "SUCCEEDED",
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: "CREATED",
        baseSha: input.workItem.baseShas[targetPath] ?? null,
        contentSha: sha256(content),
        content
      }
    ],
    eval: {
      rawFilesSeen: [],
      rawMirrorConverted: false,
      placeholderIntroduced: false,
      wikilinksCreated: 1
    }
  };
}
