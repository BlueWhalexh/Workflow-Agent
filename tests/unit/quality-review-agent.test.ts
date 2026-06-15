import { describe, expect, it } from "vitest";
import { runQualityReviewAgent } from "../../src/agents/quality-review-agent.js";

describe("quality review agent", () => {
  it("emits structured warning findings with evidence", async () => {
    const result = await runQualityReviewAgent({
      noteContents: [
        `# Topic

## 摘要

缺少关系链接。
`
      ]
    });

    expect(result.issues).toEqual(["TOPIC_NOTE_WEAK_RELATIONS"]);
    expect(result.findings).toEqual([
      {
        issue: "TOPIC_NOTE_WEAK_RELATIONS",
        severity: "warning",
        evidence: "note missing ## 相关链接"
      }
    ]);
  });
});
