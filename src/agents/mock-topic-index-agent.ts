import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import { sha256 } from "../storage/sha.js";
import type { WorkItemAgentInput } from "./work-item-agent.js";
import { buildLoopReport, budgetForWorkItemType, writeLoopReport } from "./work-item-agent-runtime.js";

export async function runMockTopicIndexAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const topicSlug = targetPath
    .replace(/^knowledge-base\/topics\//, "")
    .replace(/\/index\.md$/, "");
  const topicLabel = topicSlug.split("/").at(-1) ?? "topic";
  const content = `# ${topicLabel} Topic Index

- Organized topic notes for ${topicLabel}.
`;
  if (input.store) {
    await writeLoopReport(
      input.store,
      buildLoopReport({
        runId: input.runId,
        workItemId: input.workItem.id,
        workItemType: input.workItem.type,
        agentNode: "topic-index",
        status: "SUCCEEDED",
        budget: budgetForWorkItemType(input.workItem.type),
        usage: { iterations: 1, providerCalls: 0 },
        steps: [
          {
            name: "DRAFT_TOPIC_INDEX",
            kind: "draft",
            status: "SUCCEEDED",
            issues: [],
            repairedIssues: []
          }
        ],
        repairedIssues: [],
        remainingIssues: [],
        outputRef: { kind: "patch", path: `patches/${input.workItem.id}.patch.json` }
      })
    );
  }

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
