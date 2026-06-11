export interface PatchFile {
  path: string;
  changeType: "CREATED" | "MODIFIED";
  baseSha: string | null;
  contentSha: string;
  content: string;
}

export interface PatchBundle {
  workItemId: string;
  status: "SUCCEEDED";
  targetPaths: string[];
  files: PatchFile[];
  eval: {
    rawFilesSeen: string[];
    rawMirrorConverted: boolean;
    placeholderIntroduced: boolean;
    wikilinksCreated: number;
  };
  mergeEvidence?: {
    preservedSections: string[];
    rewrittenSections: string[];
    userContentDropped: string[];
    conflicts: string[];
  };
}
