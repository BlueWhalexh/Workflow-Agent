import type {
  ChangesetOperation,
  KnowledgeChangeset,
  NotePath,
  ValidatorFailure,
  ValidatorReport
} from "../../../runtime/core/types.js";

export interface DeterministicRuleset {
  writableRoots: string[];
  frontmatterSchema?: FrontmatterObjectSchema;
  linkPolicy?: {
    requireResolvableInternalLinks?: boolean;
  };
  sourceAttribution?: {
    requiredForUpdate?: boolean;
    rangeRequired?: boolean;
  };
  maxOperationsPerChangeset?: number;
  maxBytesPerChangeset?: number;
  trashPolicy?: {
    allowed?: boolean;
  };
}

export interface FrontmatterObjectSchema {
  required?: string[];
  properties?: Record<string, FrontmatterPropertySchema>;
}

export interface FrontmatterPropertySchema {
  type?: "string";
  minLength?: number;
  enum?: string[];
  pattern?: string;
}

export interface ValidateKnowledgeChangesetInput {
  ruleset: DeterministicRuleset;
  existingNotePaths: Iterable<NotePath>;
}

export function validateKnowledgeChangeset(
  changeset: KnowledgeChangeset,
  input: ValidateKnowledgeChangesetInput
): ValidatorReport {
  const existingNotePaths = new Set(input.existingNotePaths);
  const createdNotePaths = new Set(
    changeset.operations.filter((operation) => operation.kind === "create").map((operation) => operation.path)
  );
  const failures: ValidatorFailure[] = [];

  validateBudget(changeset.operations, input.ruleset, failures);

  changeset.operations.forEach((operation, index) => {
    validatePathWhitelist(operation, index, input.ruleset, failures);
    validateFrontmatter(operation, index, input.ruleset, failures);
    validateLinkIntegrity(operation, index, input.ruleset, existingNotePaths, createdNotePaths, failures);
    validateSourceAttribution(operation, index, changeset, input.ruleset, failures);
    validateTrashPolicy(operation, index, input.ruleset, failures);
  });

  validateRenameConflicts(changeset.operations, existingNotePaths, failures);

  return {
    passed: failures.filter((failure) => failure.severity === "error").length === 0,
    failures
  };
}

function validateBudget(
  operations: ChangesetOperation[],
  ruleset: DeterministicRuleset,
  failures: ValidatorFailure[]
): void {
  if (ruleset.maxOperationsPerChangeset !== undefined && operations.length > ruleset.maxOperationsPerChangeset) {
    failures.push({
      ruleId: "budget-limit",
      opIndex: -1,
      severity: "error",
      message: `Changeset has ${operations.length} operations, above the limit ${ruleset.maxOperationsPerChangeset}`
    });
  }

  const totalBytes = operations.reduce((sum, operation) => sum + Buffer.byteLength(operationContent(operation), "utf8"), 0);
  if (ruleset.maxBytesPerChangeset !== undefined && totalBytes > ruleset.maxBytesPerChangeset) {
    failures.push({
      ruleId: "budget-limit",
      opIndex: -1,
      severity: "error",
      message: `Changeset writes ${totalBytes} bytes, above the limit ${ruleset.maxBytesPerChangeset}`
    });
  }
}

function validatePathWhitelist(
  operation: ChangesetOperation,
  index: number,
  ruleset: DeterministicRuleset,
  failures: ValidatorFailure[]
): void {
  const paths = operation.kind === "rename" ? [operation.path, operation.targetPath] : [operation.path];
  for (const path of paths) {
    if (!ruleset.writableRoots.some((root) => path.startsWith(root))) {
      failures.push({
        ruleId: "path-whitelist",
        opIndex: index,
        severity: "error",
        message: `Path ${path} is outside writable roots`
      });
    }
  }
}

function validateFrontmatter(
  operation: ChangesetOperation,
  index: number,
  ruleset: DeterministicRuleset,
  failures: ValidatorFailure[]
): void {
  if (operation.kind !== "create" && operation.kind !== "update") {
    return;
  }

  const schema = ruleset.frontmatterSchema;
  if (!schema) {
    return;
  }

  const frontmatter = operation.frontmatter ?? {};
  for (const field of schema.required ?? []) {
    if (frontmatter[field] === undefined || frontmatter[field] === null || frontmatter[field] === "") {
      failures.push(frontmatterFailure(index, `Frontmatter field ${field} is required`));
    }
  }

  for (const [field, propertySchema] of Object.entries(schema.properties ?? {})) {
    const value = frontmatter[field];
    if (value === undefined || value === null) {
      continue;
    }
    if (propertySchema.type === "string" && typeof value !== "string") {
      failures.push(frontmatterFailure(index, `Frontmatter field ${field} must be a string`));
      continue;
    }
    if (typeof value !== "string") {
      continue;
    }
    if (propertySchema.minLength !== undefined && value.length < propertySchema.minLength) {
      failures.push(frontmatterFailure(index, `Frontmatter field ${field} is shorter than ${propertySchema.minLength}`));
    }
    if (propertySchema.enum && !propertySchema.enum.includes(value)) {
      failures.push(frontmatterFailure(index, `Frontmatter field ${field} is not an allowed value`));
    }
    if (propertySchema.pattern && !new RegExp(propertySchema.pattern).test(value)) {
      failures.push(frontmatterFailure(index, `Frontmatter field ${field} does not match the required pattern`));
    }
  }
}

function validateLinkIntegrity(
  operation: ChangesetOperation,
  index: number,
  ruleset: DeterministicRuleset,
  existingNotePaths: ReadonlySet<NotePath>,
  createdNotePaths: ReadonlySet<NotePath>,
  failures: ValidatorFailure[]
): void {
  if (!ruleset.linkPolicy?.requireResolvableInternalLinks || (operation.kind !== "create" && operation.kind !== "update")) {
    return;
  }

  const availablePaths = new Set([...existingNotePaths, ...createdNotePaths]);
  for (const link of extractInternalLinks(operation.content)) {
    if (!isResolvableLink(link, availablePaths)) {
      failures.push({
        ruleId: "link-integrity",
        opIndex: index,
        severity: "error",
        message: `Internal link ${link} does not resolve to a known note`
      });
    }
  }
}

function validateSourceAttribution(
  operation: ChangesetOperation,
  index: number,
  changeset: KnowledgeChangeset,
  ruleset: DeterministicRuleset,
  failures: ValidatorFailure[]
): void {
  if (operation.kind !== "update" || !ruleset.sourceAttribution?.requiredForUpdate) {
    return;
  }

  const hasSource = changeset.sources.some((source) => {
    if (source.notePath !== operation.path) {
      return false;
    }
    if (!ruleset.sourceAttribution?.rangeRequired) {
      return true;
    }
    return source.range.startLine > 0 && source.range.endLine >= source.range.startLine;
  });

  if (!hasSource) {
    failures.push({
      ruleId: "source-attribution",
      opIndex: index,
      severity: "error",
      message: `Update operation ${operation.path} requires source attribution`
    });
  }
}

function validateTrashPolicy(
  operation: ChangesetOperation,
  index: number,
  ruleset: DeterministicRuleset,
  failures: ValidatorFailure[]
): void {
  if (operation.kind !== "trash") {
    return;
  }
  if (ruleset.trashPolicy?.allowed === false) {
    failures.push({
      ruleId: "trash-policy",
      opIndex: index,
      severity: "error",
      message: "Trash operations are disabled by this ruleset"
    });
    return;
  }
  if (!operation.reason?.trim()) {
    failures.push({
      ruleId: "trash-policy",
      opIndex: index,
      severity: "error",
      message: "Trash operations require a reason"
    });
  }
}

function validateRenameConflicts(
  operations: ChangesetOperation[],
  existingNotePaths: ReadonlySet<NotePath>,
  failures: ValidatorFailure[]
): void {
  const operationPaths = new Set(operations.map((operation) => operation.path));

  operations.forEach((operation, index) => {
    if (operation.kind !== "rename") {
      return;
    }
    if (existingNotePaths.has(operation.targetPath) || operationPaths.has(operation.targetPath)) {
      failures.push({
        ruleId: "rename-conflict",
        opIndex: index,
        severity: "error",
        message: `Rename target ${operation.targetPath} conflicts with an existing or proposed note`
      });
    }
  });
}

function extractInternalLinks(content: string): string[] {
  const links: string[] = [];
  for (const match of content.matchAll(/\[\[([^\]|#]+)(?:#[^\]|]+)?(?:\|[^\]]+)?\]\]/g)) {
    links.push(match[1]?.trim() ?? "");
  }
  for (const match of content.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)) {
    const target = match[1]?.trim() ?? "";
    if (target && !isExternalLink(target)) {
      links.push(stripAnchor(target));
    }
  }
  return links.filter(Boolean);
}

function isResolvableLink(link: string, availablePaths: ReadonlySet<NotePath>): boolean {
  const normalized = stripMarkdownExtension(link.replace(/^\.\//, ""));
  for (const path of availablePaths) {
    const normalizedPath = stripMarkdownExtension(path);
    const basename = normalizedPath.split("/").at(-1);
    if (normalized === normalizedPath || normalized === basename || `${normalized}.md` === path) {
      return true;
    }
  }
  return false;
}

function operationContent(operation: ChangesetOperation): string {
  return operation.kind === "create" || operation.kind === "update" ? operation.content : "";
}

function frontmatterFailure(opIndex: number, message: string): ValidatorFailure {
  return {
    ruleId: "frontmatter-schema",
    opIndex,
    severity: "error",
    message
  };
}

function stripMarkdownExtension(value: string): string {
  return value.endsWith(".md") ? value.slice(0, -3) : value;
}

function stripAnchor(value: string): string {
  return value.split("#", 1)[0] ?? value;
}

function isExternalLink(value: string): boolean {
  return /^[a-z][a-z0-9+.-]*:/i.test(value) || value.startsWith("#");
}
