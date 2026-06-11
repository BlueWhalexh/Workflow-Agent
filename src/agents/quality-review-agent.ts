export interface QualityFindings {
  issues: string[];
}

export async function runQualityReviewAgent(input: { noteContents: string[] }): Promise<QualityFindings> {
  const issues = input.noteContents
    .filter((content) => !content.includes("## 相关链接"))
    .map(() => "TOPIC_NOTE_WEAK_RELATIONS");

  return { issues };
}
