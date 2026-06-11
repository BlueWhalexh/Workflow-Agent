import { listMarkdownFiles, readWorkspaceFile } from "../../storage/workspace-fs.js";
import { sha256 } from "../../storage/sha.js";
import { detectPageState, type PageState } from "./page-state.js";

export interface InventoryFile {
  path: string;
  sha: string;
  title: string | null;
  headings: string[];
}

export interface KnowledgeBasePage extends InventoryFile {
  state: PageState;
}

export interface WorkspaceInventory {
  workspaceRoot: string;
  rawFiles: InventoryFile[];
  schemaFiles: InventoryFile[];
  knowledgeBasePages: KnowledgeBasePage[];
  rawMirrorCandidates: string[];
  placeholderCandidates: string[];
  mocPath: string | null;
  topicIndexPaths: string[];
}

function extractTitle(content: string): string | null {
  const line = content.split(/\r?\n/).find((item) => item.startsWith("# "));
  return line ? line.replace(/^#\s+/, "").trim() : null;
}

function extractHeadings(content: string): string[] {
  return content
    .split(/\r?\n/)
    .filter((line) => /^#{1,6}\s+/.test(line))
    .map((line) => line.trim());
}

async function readInventoryFile(workspaceRoot: string, relativePath: string): Promise<InventoryFile> {
  const content = await readWorkspaceFile(workspaceRoot, relativePath);
  return {
    path: relativePath,
    sha: sha256(content),
    title: extractTitle(content),
    headings: extractHeadings(content)
  };
}

export async function scanWorkspace(input: { workspaceRoot: string }): Promise<WorkspaceInventory> {
  const rawPaths = await listMarkdownFiles(input.workspaceRoot, "raw");
  const schemaPaths = await listMarkdownFiles(input.workspaceRoot, "schema");
  const knowledgePaths = await listMarkdownFiles(input.workspaceRoot, "knowledge-base");

  const rawFiles = await Promise.all(rawPaths.map((filePath) => readInventoryFile(input.workspaceRoot, filePath)));
  const schemaFiles = await Promise.all(
    schemaPaths.map((filePath) => readInventoryFile(input.workspaceRoot, filePath))
  );

  const knowledgeBasePages = await Promise.all(
    knowledgePaths.map(async (filePath) => {
      const content = await readWorkspaceFile(input.workspaceRoot, filePath);
      return {
        path: filePath,
        sha: sha256(content),
        title: extractTitle(content),
        headings: extractHeadings(content),
        state: detectPageState(content)
      };
    })
  );

  return {
    workspaceRoot: input.workspaceRoot,
    rawFiles,
    schemaFiles,
    knowledgeBasePages,
    rawMirrorCandidates: knowledgeBasePages
      .filter((page) => page.state === "BOOTSTRAP_MIRROR")
      .map((page) => page.path),
    placeholderCandidates: knowledgeBasePages.filter((page) => page.title === null).map((page) => page.path),
    mocPath: knowledgePaths.includes("knowledge-base/moc.md") ? "knowledge-base/moc.md" : null,
    topicIndexPaths: knowledgePaths.filter((filePath) => filePath.endsWith("/index.md"))
  };
}
