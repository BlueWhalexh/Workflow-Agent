import { promises as fs } from "node:fs";
import path from "node:path";
import { stableJson } from "./json-schema.js";

export class AgentRunsStore {
  private readonly runRoot: string;

  static async latestRunId(workspaceRoot: string): Promise<string | null> {
    try {
      const entries = await fs.readdir(path.join(workspaceRoot, ".agent-runs"), { withFileTypes: true });
      const runIds = entries
        .filter((entry) => entry.isDirectory())
        .map((entry) => entry.name)
        .sort();
      return runIds.at(-1) ?? null;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") {
        return null;
      }
      throw error;
    }
  }

  constructor(
    private readonly workspaceRoot: string,
    private readonly runId: string
  ) {
    this.runRoot = path.join(workspaceRoot, ".agent-runs", runId);
  }

  artifactPath(relativePath: string): string {
    return path.join(this.runRoot, relativePath);
  }

  async writeJson(relativePath: string, value: unknown): Promise<void> {
    const absolutePath = this.artifactPath(relativePath);
    await fs.mkdir(path.dirname(absolutePath), { recursive: true });
    const tempPath = `${absolutePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, stableJson(value), "utf8");
    await fs.rename(tempPath, absolutePath);
  }

  async readJson<T>(relativePath: string): Promise<T> {
    const content = await fs.readFile(this.artifactPath(relativePath), "utf8");
    return JSON.parse(content) as T;
  }

  async writeText(relativePath: string, value: string): Promise<void> {
    const absolutePath = this.artifactPath(relativePath);
    await fs.mkdir(path.dirname(absolutePath), { recursive: true });
    const tempPath = `${absolutePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, value.endsWith("\n") ? value : `${value}\n`, "utf8");
    await fs.rename(tempPath, absolutePath);
  }
}
