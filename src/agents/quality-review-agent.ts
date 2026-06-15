export interface QualityFindings {
  issues: string[];
  findings: Array<{
    issue: string;
    severity: "warning";
    targetPath?: string;
    evidence: string;
  }>;
}

export async function runQualityReviewAgent(input: { noteContents: string[] }): Promise<QualityFindings> {
  const findings = input.noteContents
    .filter((content) => !content.includes("## 相关链接"))
    .map(() => ({
      issue: "TOPIC_NOTE_WEAK_RELATIONS",
      severity: "warning" as const,
      evidence: "note missing ## 相关链接"
    }));
  const issues = findings.map((finding) => finding.issue);

  return { issues, findings };
}
