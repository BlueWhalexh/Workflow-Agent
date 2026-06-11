import type { WorkItem } from "../planning/work-item.js";
import type { ValidationResult } from "./validator.js";

export interface EvalReport {
  rawCoverage: {
    total: number;
    seen: number;
  };
  pagesRewritten: number;
  rawMirrorConverted: number;
  qualityIssues: string[];
  workItemStatuses: Record<string, string>;
}

export function buildEvalReport(input: {
  rawCount: number;
  rawFilesSeen: string[];
  pagesRewritten: number;
  rawMirrorConverted: number;
  workItems: WorkItem[];
  validations: ValidationResult[];
}): EvalReport {
  return {
    rawCoverage: {
      total: input.rawCount,
      seen: new Set(input.rawFilesSeen).size
    },
    pagesRewritten: input.pagesRewritten,
    rawMirrorConverted: input.rawMirrorConverted,
    qualityIssues: input.validations.flatMap((validation) => validation.qualityIssues),
    workItemStatuses: Object.fromEntries(input.workItems.map((item) => [item.id, item.status]))
  };
}
