export type InternalToolRisk = "READ_ONLY" | "ARTIFACT_WRITE" | "WORKSPACE_WRITE";
export type InternalToolExposure = "SDK_ONLY" | "INTERNAL_ONLY";

export interface InternalToolMetadata {
  name: string;
  description: string;
  risk: InternalToolRisk;
  publicExposure: InternalToolExposure;
}

export const internalTools: InternalToolMetadata[] = [
  {
    name: "workspace.scan",
    description: "Scan workspace files and return deterministic inventory.",
    risk: "READ_ONLY",
    publicExposure: "SDK_ONLY"
  },
  {
    name: "artifact.readPlan",
    description: "Read plan artifact for a run.",
    risk: "READ_ONLY",
    publicExposure: "SDK_ONLY"
  },
  {
    name: "artifact.readEval",
    description: "Read eval artifact for a run.",
    risk: "READ_ONLY",
    publicExposure: "SDK_ONLY"
  },
  {
    name: "plan.createOrganizePlan",
    description: "Create a methodology-aware organize plan.",
    risk: "ARTIFACT_WRITE",
    publicExposure: "INTERNAL_ONLY"
  },
  {
    name: "patch.checkMerge",
    description: "Check whether a patch can be safely merged.",
    risk: "READ_ONLY",
    publicExposure: "INTERNAL_ONLY"
  },
  {
    name: "patch.validate",
    description: "Validate a patch bundle against methodology rules.",
    risk: "READ_ONLY",
    publicExposure: "SDK_ONLY"
  },
  {
    name: "patch.publish",
    description: "Publish a validated patch bundle to the workspace.",
    risk: "WORKSPACE_WRITE",
    publicExposure: "INTERNAL_ONLY"
  },
  {
    name: "report.aggregateEval",
    description: "Aggregate run artifacts into eval/report summaries.",
    risk: "ARTIFACT_WRITE",
    publicExposure: "INTERNAL_ONLY"
  }
];

export const publiclyExposedToolNames = internalTools
  .filter((tool) => tool.publicExposure === "SDK_ONLY")
  .map((tool) => tool.name);
