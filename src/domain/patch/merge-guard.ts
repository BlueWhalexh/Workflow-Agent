import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { sha256 } from "../../storage/sha.js";
import type { PatchBundle } from "./patch-bundle.js";

export interface MergeDecision {
  allowed: boolean;
  reasons: string[];
}

export async function checkMerge(input: {
  workspaceRoot: string;
  authorizedTargetPaths: string[];
  bundle: PatchBundle;
}): Promise<MergeDecision> {
  const reasons: string[] = [];
  const authorized = new Set(input.authorizedTargetPaths);

  for (const file of input.bundle.files) {
    if (file.path.startsWith("raw/") || file.path.startsWith("schema/")) {
      reasons.push("RAW_OR_SCHEMA_WRITE_BLOCKED");
    }
    if (!authorized.has(file.path)) {
      reasons.push(`UNAUTHORIZED_PATH:${file.path}`);
    }
    if (!input.bundle.targetPaths.includes(file.path)) {
      reasons.push(`TARGET_PATH_MISMATCH:${file.path}`);
    }
    if (sha256(file.content) !== file.contentSha) {
      reasons.push(`CONTENT_SHA_MISMATCH:${file.path}`);
    }
    const absolutePath = path.join(input.workspaceRoot, file.path);
    if (file.baseSha !== null && existsSync(absolutePath)) {
      const currentSha = sha256(readFileSync(absolutePath, "utf8"));
      if (currentSha !== file.baseSha) {
        reasons.push(`BASE_SHA_MISMATCH:${file.path}`);
      }
    }
  }

  return {
    allowed: reasons.length === 0,
    reasons
  };
}
