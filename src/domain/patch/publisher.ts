import { promises as fs } from "node:fs";
import path from "node:path";
import type { PatchBundle } from "./patch-bundle.js";

export async function publishBundle(input: {
  workspaceRoot: string;
  bundle: PatchBundle;
}): Promise<{ publishedPaths: string[] }> {
  const publishedPaths: string[] = [];

  for (const file of input.bundle.files) {
    const absolutePath = path.join(input.workspaceRoot, file.path);
    await fs.mkdir(path.dirname(absolutePath), { recursive: true });
    await fs.writeFile(absolutePath, file.content, "utf8");
    publishedPaths.push(file.path);
  }

  return { publishedPaths };
}
