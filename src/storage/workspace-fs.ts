import { promises as fs } from "node:fs";
import path from "node:path";

export async function listMarkdownFiles(root: string, relativeDir: string): Promise<string[]> {
  const absoluteDir = path.join(root, relativeDir);
  try {
    const entries = await fs.readdir(absoluteDir, { withFileTypes: true });
    const nested = await Promise.all(
      entries.map(async (entry) => {
        const relativePath = path.join(relativeDir, entry.name);
        if (entry.isDirectory()) {
          return listMarkdownFiles(root, relativePath);
        }
        if (entry.isFile() && entry.name.endsWith(".md")) {
          return [relativePath.split(path.sep).join("/")];
        }
        return [];
      })
    );
    return nested.flat().sort();
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return [];
    }
    throw error;
  }
}

export async function readWorkspaceFile(root: string, relativePath: string): Promise<string> {
  return fs.readFile(path.join(root, relativePath), "utf8");
}
