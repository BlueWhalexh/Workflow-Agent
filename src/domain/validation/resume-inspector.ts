import { readFile } from "node:fs/promises";
import path from "node:path";
import type { WorkItemStatus } from "../planning/work-item.js";
import { sha256 } from "../../storage/sha.js";
import { decideResumeAction, type ResumeAction } from "./resume-decision.js";

export interface ResumeInspectableWorkItem {
  id: string;
  status: WorkItemStatus;
  targetPaths: string[];
  contentSha?: string;
  retryable?: boolean;
}

export interface ResumeInspection {
  workItemId: string;
  action: ResumeAction;
  targetPath: string | null;
  currentSha: string | null;
  contentSha: string | null;
}

export async function inspectResumeWorkItem(input: {
  workspaceRoot: string;
  workItem: ResumeInspectableWorkItem;
}): Promise<ResumeInspection> {
  const targetPath = input.workItem.targetPaths[0] ?? null;
  let currentSha: string | null = null;

  if (targetPath) {
    try {
      const content = await readFile(path.join(input.workspaceRoot, targetPath), "utf8");
      currentSha = sha256(content);
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
        throw error;
      }
    }
  }

  return {
    workItemId: input.workItem.id,
    targetPath,
    currentSha,
    contentSha: input.workItem.contentSha ?? null,
    action: decideResumeAction({
      status: input.workItem.status,
      currentSha: currentSha ?? undefined,
      contentSha: input.workItem.contentSha,
      retryable: input.workItem.retryable
    })
  };
}
