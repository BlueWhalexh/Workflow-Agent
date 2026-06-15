import { describe, expect, it } from "vitest";
import {
  getKnowledgeMethodology,
  listKnowledgeMethodologies
} from "../../src/domain/methodology/knowledge-methodology.js";

describe("knowledge methodology registry", () => {
  it("returns lmwiki-v1 as the default methodology", () => {
    const methodology = getKnowledgeMethodology();

    expect(methodology.id).toBe("lmwiki-v1");
    expect(methodology.layout.rawDir).toBe("raw");
    expect(methodology.layout.rulesDir).toBe("schema");
    expect(methodology.layout.knowledgeBaseDir).toBe("knowledge-base");
    expect(methodology.layout.topicDir).toBe("knowledge-base/topics");
    expect(methodology.layout.mocPath).toBe("knowledge-base/moc.md");
    expect(methodology.noteSchema.requiredSections).toContain("摘要");
    expect(methodology.noteSchema.acceptedSectionAliases["来源追踪"]).toContain("Source Tracking");
  });

  it("lists registered methodologies without exposing mutable profile objects", () => {
    expect(listKnowledgeMethodologies()).toEqual([
      { id: "lmwiki-v1", displayName: "LMWiki", version: "1" }
    ]);
  });

  it("rejects unknown methodology ids", () => {
    expect(() => getKnowledgeMethodology("unknown")).toThrow("Unknown knowledge methodology: unknown");
  });
});
